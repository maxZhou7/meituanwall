package com.meituan.android.walle

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

        // AGP 8.x version check
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

        applyExtension(project)
        applyTask(project)
    }

    private fun applyExtension(project: Project) {
        project.extensions.create(PLUGIN_EXTENSION_NAME, Extension::class.java, project)
    }

    private fun applyTask(project: Project) {
        project.afterEvaluate {
            // AGP 9.0 uses androidComponents API
            val androidComponents = project.extensions.findByName("androidComponents")
            if (androidComponents != null) {
                // AGP 9.0+ approach using new Variant API
                try {
                    val onVariantsMethod = androidComponents.javaClass.getMethod("onVariants", Function1::class.java)
                    @Suppress("UNCHECKED_CAST")
                    val consumer = { variant: Any ->
                        val variantName = variant.javaClass.getMethod("getName").invoke(variant) as String
                        val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }

                        // Register clean channel folder task
                        val cleanChannelTask = project.tasks.register("clean${capitalizedVariantName}ChannelFolder") { task ->
                            task.description = "Clean channel output folder before packaging"
                            task.group = "Package"

                            task.doLast {
                                val extension = Extension.getConfig(project)
                                val channelOutputFolder = extension.apkOutputFolder

                                // If custom output folder is configured, clean it
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
                                    // Otherwise clean the default output location
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

                        // Use register() instead of create() for lazy task configuration
                        val channelMakerTask = project.tasks.register(
                            "assemble${capitalizedVariantName}Channels",
                            ChannelMaker::class.java
                        ) { task ->
                            task.targetProject = project
                            // Pass variant name and use adapter for AGP 9.0
                            task.variantName = variantName
                            task.setup()

                            // AGP 9.0 compatibility - depend on assemble task
                            task.dependsOn("assemble$capitalizedVariantName")
                            // Make channel maker depend on clean task
                            task.dependsOn(cleanChannelTask)
                        }
                    }
                    onVariantsMethod.invoke(androidComponents, consumer)
                } catch (e: Exception) {
                    project.logger.warn("Could not register tasks with androidComponents: ${e.message}")
                }
            } else {
                // Fallback for older AGP versions (8.x and below)
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
                                project.logger.warn("Warning: APK Signature Scheme v2 may not be enabled for $variantName.")
                            }

                            // Register clean channel folder task
                            val cleanChannelTask = project.tasks.register("clean${capitalizedVariantName}ChannelFolder") { task ->
                                task.description = "Clean channel output folder before packaging"
                                task.group = "Package"

                                task.doLast {
                                    val extension = Extension.getConfig(project)
                                    val channelOutputFolder = extension.apkOutputFolder

                                    // If custom output folder is configured, clean it
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
                                        // Otherwise clean the default output location
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

                            // Use register() instead of create() for lazy task configuration
                            val channelMakerTask = project.tasks.register(
                                "assemble${capitalizedVariantName}Channels",
                                ChannelMaker::class.java
                            ) { task ->
                                task.targetProject = project
                                task.variant = variant
                                task.setup()

                                // AGP 8.x compatibility
                                try {
                                    val assembleProvider = variant.javaClass.getMethod("getAssembleProvider")
                                        .invoke(variant)
                                    task.dependsOn(assembleProvider.javaClass.getMethod("get").invoke(assembleProvider))
                                    // Make channel maker depend on clean task
                                    task.dependsOn(cleanChannelTask)
                                } catch (e: Exception) {
                                    task.dependsOn(variant.javaClass.getMethod("getAssemble").invoke(variant))
                                    task.dependsOn(cleanChannelTask)
                                }
                            }
                        }
                    }
                } catch (e: ClassNotFoundException) {
                    project.logger.warn("AppExtension not available (AGP 9.0+): ${e.message}")
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
