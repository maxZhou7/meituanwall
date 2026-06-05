package com.meituan.android.walle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Enumeration
import java.util.jar.JarFile
import java.util.jar.Manifest

class GradlePlugin : Plugin<Project> {

    companion object {
        const val PLUGIN_EXTENSION_NAME = "walle"

        @JvmStatic
        private fun versionCompare(str1: String, str2: String): Int {
            val vals1 = str1.split("-")[0].split(".")
            val vals2 = str2.split("-")[0].split(".")
            var i = 0
            while (i < vals1.size && i < vals2.size && vals1[i] == vals2[i]) {
                i++
            }

            return if (i < vals1.size && i < vals2.size) {
                Integer.signum(vals1[i].toInt().compareTo(vals2[i].toInt()))
            } else {
                Integer.signum(vals1.size - vals2.size)
            }
        }

        @JvmStatic
        private fun getVersion(): String? {
            try {
                val resEnum: Enumeration<URL> = Thread.currentThread().contextClassLoader.getResources(JarFile.MANIFEST_NAME)
                while (resEnum.hasMoreElements()) {
                    try {
                        val url = resEnum.nextElement()
                        val inputStream = url.openStream()
                        if (inputStream != null) {
                            val manifest = Manifest(inputStream)
                            val mainAttribs = manifest.mainAttributes
                            val version = mainAttribs.getValue("Walle-Version")
                            if (version != null) {
                                return version
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e1: IOException) {
                e1.printStackTrace()
            }
            return null
        }
    }

    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw ProjectConfigurationException(
                "Plugin requires the 'com.android.application' plugin to be configured.",
                emptyList()
            )
        }

        // Detect AGP version
        var agpVersion: String? = null
        try {
            val clazz = Class.forName("com.android.Version")
            val field = clazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            field.isAccessible = true
            agpVersion = field.get(null) as? String
        } catch (ignore: ClassNotFoundException) {
            try {
                val clazz = Class.forName("com.android.builder.model.Version")
                val field = clazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
                field.isAccessible = true
                agpVersion = field.get(null) as? String
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (agpVersion != null && versionCompare(agpVersion, "8.0.0") < 0) {
            project.logger.warn("Walle plugin is optimized for AGP 8.0+. Current version: $agpVersion")
        }

        project.logger.info("[Walle] Detected AGP version: $agpVersion")

        applyExtension(project)
        // Pass AGP version to determine which API to use
        val isAgp9Plus = agpVersion != null && versionCompare(agpVersion, "9.0.0") >= 0
        applyTask(project, isAgp9Plus)
    }

    private fun applyExtension(project: Project) {
        project.extensions.create(PLUGIN_EXTENSION_NAME, Extension::class.java, project)
    }

    private fun applyTask(project: Project, isAgp9Plus: Boolean) {
        if (isAgp9Plus) {
            // ========== AGP 9.0+ approach: call onVariants directly during configuration ==========
            // Cannot use afterEvaluate as onVariants callbacks have already executed by then
            val androidComponents = project.extensions.findByName("androidComponents")
            if (androidComponents == null) {
                project.logger.warn("[Walle] androidComponents extension not found, cannot register channel tasks")
                return
            }

            val consumer = { variant: Any ->
                val variantName = variant.javaClass.getMethod("getName").invoke(variant) as String
                val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }

                val cleanChannelTask = createCleanChannelTask(project, variantName, capitalizedVariantName)

                project.tasks.register(
                    "assemble${capitalizedVariantName}Channels",
                    ChannelMaker::class.java
                ) { task ->
                    task.targetProject = project
                    task.variantName = variantName
                    task.setup()
                    task.dependsOn("assemble$capitalizedVariantName")
                    task.dependsOn(cleanChannelTask)
                }
            }

            // Find onVariants(VariantSelector, Function1) method
            var onVariantsMethod: java.lang.reflect.Method? = null
            var useAction = false
            try {
                onVariantsMethod = androidComponents.javaClass.getMethod("onVariants",
                    Class.forName("com.android.build.api.variant.VariantSelector"),
                    Function1::class.java)
            } catch (e: NoSuchMethodException) {
                try {
                    onVariantsMethod = androidComponents.javaClass.getMethod("onVariants",
                        Class.forName("com.android.build.api.variant.VariantSelector"),
                        Action::class.java)
                    useAction = true
                } catch (e2: NoSuchMethodException) {
                    project.logger.warn("[Walle] onVariants(VariantSelector, callback) not found")
                }
            }

            if (onVariantsMethod != null) {
                val selectorMethod = androidComponents.javaClass.getMethod("selector")
                val selector = selectorMethod.invoke(androidComponents)
                selector.javaClass.getMethod("all").invoke(selector)

                val callback = if (useAction) Action<Any> { consumer(it) } else consumer
                onVariantsMethod.invoke(androidComponents, selector, callback)
            }
        } else {
            // ========== AGP 8.x approach: use afterEvaluate with applicationVariants ==========
            project.afterEvaluate {
                try {
                    val appExtensionClass = Class.forName("com.android.build.gradle.AppExtension")
                    val androidExt = project.extensions.findByType(appExtensionClass)
                    if (androidExt != null) {
                        val applicationVariants = androidExt.javaClass.getMethod("getApplicationVariants")
                            .invoke(androidExt) as? Iterable<Any>
                        applicationVariants?.forEach { variant ->
                            val variantName = variant.javaClass.getMethod("getName").invoke(variant) as String
                            val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }

                            if (!isV2SignatureSchemeEnabled(variant, project)) {
                                project.logger.warn("[Walle] APK Signature Scheme v2 may not be enabled for $variantName.")
                            }

                            val cleanChannelTask = createCleanChannelTaskAgp8(project, variantName, capitalizedVariantName)

                            project.tasks.register(
                                "assemble${capitalizedVariantName}Channels",
                                ChannelMaker::class.java
                            ) { task ->
                                task.targetProject = project
                                task.variant = variant
                                task.setup()

                                try {
                                    val assembleProvider = variant.javaClass.getMethod("getAssembleProvider")
                                        .invoke(variant)
                                    task.dependsOn(assembleProvider.javaClass.getMethod("get").invoke(assembleProvider))
                                    task.dependsOn(cleanChannelTask)
                                } catch (e: Exception) {
                                    task.dependsOn(variant.javaClass.getMethod("getAssemble").invoke(variant))
                                    task.dependsOn(cleanChannelTask)
                                }
                            }
                        }
                    }
                } catch (e: ClassNotFoundException) {
                    project.logger.warn("[Walle] AppExtension not available: ${e.message}")
                }
            }
        }
    }

    /**
     * Create clean channel folder task for AGP 9.0+ (uses layout.buildDirectory)
     */
    private fun createCleanChannelTask(project: Project, variantName: String, capitalizedVariantName: String) =
        project.tasks.register("clean${capitalizedVariantName}ChannelFolder") { task ->
            task.description = "Clean channel output folder before packaging"
            task.group = "Package"
            task.doLast {
                val extension = Extension.getConfig(project)
                val channelOutputFolder = extension.apkOutputFolder
                if (channelOutputFolder is File && channelOutputFolder.exists()) {
                    project.logger.lifecycle("[Walle] Cleaning channel output folder: ${channelOutputFolder.absolutePath}")
                    var deletedCount = 0
                    channelOutputFolder.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".apk")) {
                            f.delete()
                            deletedCount++
                            project.logger.info("[Walle] Deleted: ${f.name}")
                        }
                    }
                    project.logger.lifecycle("[Walle] Deleted $deletedCount APK file(s)")
                } else {
                    val defaultOutputFolder = File(
                        project.layout.buildDirectory.get().asFile,
                        "outputs/apk/$variantName"
                    )
                    if (defaultOutputFolder.exists()) {
                        project.logger.lifecycle("[Walle] Cleaning default output folder: ${defaultOutputFolder.absolutePath}")
                        var deletedCount = 0
                        defaultOutputFolder.listFiles()?.forEach { f ->
                            if (f.name.endsWith(".apk")) {
                                f.delete()
                                deletedCount++
                                project.logger.info("[Walle] Deleted: ${f.name}")
                            }
                        }
                        project.logger.lifecycle("[Walle] Deleted $deletedCount APK file(s)")
                    }
                }
            }
        }

    /**
     * Create clean channel folder task for AGP 8.x (uses buildDir)
     */
    private fun createCleanChannelTaskAgp8(project: Project, variantName: String, capitalizedVariantName: String) =
        project.tasks.register("clean${capitalizedVariantName}ChannelFolder") { task ->
            task.description = "Clean channel output folder before packaging"
            task.group = "Package"
            task.doLast {
                val extension = Extension.getConfig(project)
                val channelOutputFolder = extension.apkOutputFolder
                if (channelOutputFolder is File && channelOutputFolder.exists()) {
                    project.logger.lifecycle("[Walle] Cleaning channel output folder: ${channelOutputFolder.absolutePath}")
                    var deletedCount = 0
                    channelOutputFolder.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".apk")) {
                            f.delete()
                            deletedCount++
                            project.logger.info("[Walle] Deleted: ${f.name}")
                        }
                    }
                    project.logger.lifecycle("[Walle] Deleted $deletedCount APK file(s)")
                } else {
                    val defaultOutputFolder = File(project.buildDir, "outputs/apk/$variantName")
                    if (defaultOutputFolder.exists()) {
                        project.logger.lifecycle("[Walle] Cleaning default output folder: ${defaultOutputFolder.absolutePath}")
                        var deletedCount = 0
                        defaultOutputFolder.listFiles()?.forEach { f ->
                            if (f.name.endsWith(".apk")) {
                                f.delete()
                                deletedCount++
                                project.logger.info("[Walle] Deleted: ${f.name}")
                            }
                        }
                        project.logger.lifecycle("[Walle] Deleted $deletedCount APK file(s)")
                    }
                }
            }
        }

    private fun getSigningConfig(variant: Any, project: Project): Any? {
        return try {
            // AGP 8.x approach
            if (variant.javaClass.getMethod("getSigningConfig").invoke(variant) != null) {
                variant.javaClass.getMethod("getSigningConfig").invoke(variant)
            } else {
                // Fallback to old approach
                val buildType = variant.javaClass.getMethod("getBuildType").invoke(variant)
                val mergedFlavor = variant.javaClass.getMethod("getMergedFlavor").invoke(variant)
                val buildTypeSigningConfig = buildType?.javaClass?.getMethod("getSigningConfig")?.invoke(buildType)
                val mergedFlavorSigningConfig = mergedFlavor?.javaClass?.getMethod("getSigningConfig")?.invoke(mergedFlavor)
                buildTypeSigningConfig ?: mergedFlavorSigningConfig
            }
        } catch (e: Exception) {
            project.logger.warn("Could not get signing config: ${e.message}")
            null
        }
    }

    private fun isV2SignatureSchemeEnabled(variant: Any, project: Project): Boolean {
        return try {
            val signingConfig = getSigningConfig(variant, project)
            if (signingConfig == null) {
                return false
            }

            // Check if signing config is ready
            val isSigningReady = signingConfig.javaClass.getMethod("isSigningReady").invoke(signingConfig) as? Boolean
            if (isSigningReady == false) {
                return false
            }

            // Check v2 signing enabled (AGP 8.x)
            val v2SigningEnabled = signingConfig.javaClass.getMethod("getV2SigningEnabled").invoke(signingConfig) as? Boolean
            if (v2SigningEnabled != null) {
                return v2SigningEnabled
            }

            // For newer AGP, assume v2 is enabled by default
            true
        } catch (e: Exception) {
            project.logger.warn("Could not check V2 signature scheme status: ${e.message}")
            true // Assume enabled for compatibility
        }
    }
}
