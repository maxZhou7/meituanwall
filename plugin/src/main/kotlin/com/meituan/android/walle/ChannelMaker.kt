package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.google.gson.Gson
import groovy.text.SimpleTemplateEngine
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.HashFunction
import org.gradle.internal.hash.Hashing
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

open class ChannelMaker : DefaultTask() {

    companion object {
        private const val DOT_APK = ".apk"
        private const val PROPERTY_CHANNEL_LIST = "channelList"
        private const val PROPERTY_CHANNEL_FILE = "channelFile"
        private const val PROPERTY_CONFIG_FILE = "configFile"
        private const val PROPERTY_EXTRA_INFO = "extraInfo"

        @JvmStatic
        fun checkV2Signature(apkFile: File) {
            var fIn: FileInputStream? = null
            var fChan: FileChannel? = null
            try {
                fIn = FileInputStream(apkFile)
                fChan = fIn.channel
                val fSize = fChan.size()
                val byteBuffer = ByteBuffer.allocate(fSize.toInt())
                fChan.read(byteBuffer)
                byteBuffer.rewind()

                val dataSource = ByteBufferDataSource(byteBuffer)

                val apkVerifier = ApkVerifier()
                val result = apkVerifier.verify(dataSource, 0)
                if (!result.isVerified || !result.isVerifiedUsingV2Scheme) {
                    throw GradleException("$apkFile has no v2 signature in Apk Signing Block!")
                }
            } catch (ignore: IOException) {
                ignore.printStackTrace()
            } finally {
                IOUtils.closeQuietly(fChan)
                IOUtils.closeQuietly(fIn)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getFileHash(file: File): String {
            val hashFunction: HashFunction = Hashing.sha1()
            val hashCode: HashCode = if (file.isDirectory) {
                hashFunction.hashString(file.path)
            } else {
                // Use standard Java API instead of Gradle internal Guava
                val digest = MessageDigest.getInstance("SHA-1")
                val fileBytes = FileUtils.readFileToByteArray(file)
                val hashBytes = digest.digest(fileBytes)
                hashFunction.hashBytes(hashBytes)
            }
            return hashCode.toString()
        }

        @JvmStatic
        fun getChannelListFromFile(channelFile: File): List<String> {
            val channelList = mutableListOf<String>()
            channelFile.forEachLine { line ->
                val lineTrim = line.trim()
                if (lineTrim.isNotEmpty() && !lineTrim.startsWith("#")) {
                    val channel = line.split("#")[0].trim()
                    if (channel.isNotEmpty()) {
                        channelList.add(channel)
                    }
                }
            }
            return channelList
        }
    }

    @Input
    @Optional
    var variant: Any? = null

    @Input
    var targetProject: Project? = null

    @Input
    var variantName: String? = null

    fun setup() {
        description = "Make Multi-Channel"
        group = "Package"
    }

    @TaskAction
    fun packaging() {
        val extension = Extension.getConfig(targetProject!!)

        val startTime = System.currentTimeMillis()

        // AGP 9.0 compatibility: use variantName if variant is null
        var actualVariant = variant
        if (actualVariant == null && variantName != null) {
            // Create a mock variant object for AGP 9.0
            actualVariant = createVariantAdapter(targetProject!!, variantName!!)
        }

        if (actualVariant == null) {
            throw GradleException("Variant information is not available")
        }

        val outputs = (actualVariant as? Map<String, Any?>)?.get("outputs") as? List<Map<String, Any?>>
            ?: (actualVariant.javaClass.getMethod("getOutputs").invoke(actualVariant) as? Iterable<Any>)?.let { iterable ->
                iterable.map { output ->
                    mapOf(
                        "outputFile" to output.javaClass.getMethod("getOutputFile").invoke(output) as? File,
                        "outputs" to output.javaClass.getMethod("getOutputs").invoke(output)
                    )
                }
            } ?: emptyList()

        outputs.forEach { output ->
            val apkFile = output["outputFile"] as? File
            var apiIdentifier: String? = null

            val outputList = output["outputs"] as? List<Map<String, Any?>>
            if (outputList != null && outputList.isNotEmpty()) {
                val filters = outputList[0]["filters"] as? List<Map<String, String>>
                filters?.forEach { filterData ->
                    if (filterData["filterType"] == "ABI") {
                        apiIdentifier = filterData["identifier"]
                    }
                }
            }

            if (apkFile == null || !apkFile.exists()) {
                throw GradleException("$apkFile is not existed!")
            }

            checkV2Signature(apkFile)

            var channelOutputFolder = apkFile.parentFile
            if (extension.apkOutputFolder is File) {
                channelOutputFolder = extension.apkOutputFolder!!
                if (!channelOutputFolder.parentFile.exists()) {
                    channelOutputFolder.parentFile.mkdirs()
                }
            }

            if (apiIdentifier != null && apiIdentifier!!.isNotEmpty()) {
                channelOutputFolder = File(channelOutputFolder, apiIdentifier)
                if (!channelOutputFolder.parentFile.exists()) {
                    channelOutputFolder.parentFile.mkdirs()
                }
            }

            val buildTypeName = getBuildTypeName(actualVariant)
            val versionName = getVersionName(actualVariant)
            val versionCode = getVersionCode(actualVariant)
            val applicationId = getApplicationId(actualVariant)
            val flavorName = getFlavorName(actualVariant)

            val nameVariantMap = mutableMapOf(
                "appName" to targetProject!!.name,
                "projectName" to targetProject!!.rootProject.name,
                "buildType" to buildTypeName,
                "versionName" to versionName,
                "versionCode" to versionCode,
                "packageName" to applicationId,
                "flavorName" to flavorName
            )

            when {
                targetProject!!.hasProperty(PROPERTY_CHANNEL_LIST) -> {
                    val channelList = mutableListOf<String>()
                    val channelListProperty = targetProject!!.properties[PROPERTY_CHANNEL_LIST] as? String
                    if (!channelListProperty.isNullOrBlank()) {
                        channelList.addAll(channelListProperty.split(",").map { it.trim() })
                    }

                    var extraInfo: Map<String, String>? = null
                    val extraInfoString = targetProject!!.properties[PROPERTY_EXTRA_INFO] as? String
                    if (!extraInfoString.isNullOrBlank()) {
                        extraInfo = extraInfoString.split(",")
                            .map { it.trim() }
                            .filter { it.split(":").size == 2 }
                            .associate {
                                val data = it.split(":")
                                data[0] to data[1]
                            }
                    }

                    channelList.forEach { channel ->
                        generateChannelApk(apkFile, channelOutputFolder, nameVariantMap, channel, extraInfo, null, extension)
                    }
                }

                targetProject!!.hasProperty(PROPERTY_CONFIG_FILE) -> {
                    val configFile = File(targetProject!!.properties[PROPERTY_CONFIG_FILE] as String)
                    if (!configFile.exists()) {
                        project.logger.warn("config file does not exist")
                        return
                    }
                    generateChannelApkByConfigFile(configFile, apkFile, channelOutputFolder, nameVariantMap, extension)
                }

                targetProject!!.hasProperty(PROPERTY_CHANNEL_FILE) -> {
                    val channelFile = File(targetProject!!.properties[PROPERTY_CHANNEL_FILE] as String)
                    if (!channelFile.exists()) {
                        project.logger.warn("channel file does not exist")
                        return
                    }
                    generateChannelApkByChannelFile(channelFile, apkFile, channelOutputFolder, nameVariantMap, extension)
                }

                extension.configFile is File -> {
                    if (!extension.configFile!!.exists()) {
                        project.logger.warn("config file does not exist")
                        return
                    }
                    generateChannelApkByConfigFile(extension.configFile!!, apkFile, channelOutputFolder, nameVariantMap, extension)
                }

                extension.channelFile is File -> {
                    if (!extension.channelFile!!.exists()) {
                        project.logger.warn("channel file does not exist")
                        return
                    }
                    generateChannelApkByChannelFile(extension.channelFile!!, apkFile, channelOutputFolder, nameVariantMap, extension)
                }

                !extension.variantConfigFileName.isNullOrEmpty() -> {
                    val locations = mutableListOf<File>()
                    val variantName = getVariantName(actualVariant)
                    locations.add(File(project.projectDir, "src${File.separator}$variantName"))
                    locations.add(File(project.projectDir, "src${File.separator}$flavorName"))
                    locations.add(File(project.projectDir, "src${File.separator}$buildTypeName"))
                    locations.add(File(project.projectDir, "src${File.separator}main"))

                    var isFindConfigFile = false
                    locations.forEach { file ->
                        if (isFindConfigFile) return@forEach
                        if (file.exists()) {
                            val configFile = File(file, extension.variantConfigFileName!!)
                            if (configFile.exists()) {
                                generateChannelApkByConfigFile(configFile, apkFile, channelOutputFolder, nameVariantMap, extension)
                                isFindConfigFile = true
                                project.logger.error("[Walle] Using config file : $configFile")
                            }
                        }
                    }
                    if (!isFindConfigFile) {
                        project.logger.error("[Walle] config file does not exist")
                        project.logger.error("[Walle] please put the file in the follow locations [Descending order of priority]")
                        locations.forEach { file ->
                            project.logger.error("[Walle]   ${file.absolutePath}")
                        }
                    }
                }
            }
        }

        targetProject!!.logger.lifecycle(
            "APK Signature Scheme v2 Channel Maker takes about " +
            "${System.currentTimeMillis() - startTime} milliseconds"
        )
    }

    private fun getBuildTypeName(variant: Any): String {
        return when (variant) {
            is Map<*, *> -> (variant["buildType"] as? Map<*, *>)?.get("name") as? String ?: "debug"
            else -> variant.javaClass.getMethod("getBuildType").invoke(variant)
                ?.let { buildType -> buildType.javaClass.getMethod("getName").invoke(buildType) as? String }
                ?: "debug"
        }
    }

    private fun getVersionName(variant: Any): String {
        return when (variant) {
            is Map<*, *> -> variant["versionName"] as? String ?: "1.0"
            else -> variant.javaClass.getMethod("getVersionName").invoke(variant) as? String ?: "1.0"
        }
    }

    private fun getVersionCode(variant: Any): Any {
        return when (variant) {
            is Map<*, *> -> variant["versionCode"] ?: 1
            else -> variant.javaClass.getMethod("getVersionCode").invoke(variant) ?: 1
        }
    }

    private fun getApplicationId(variant: Any): String {
        return when (variant) {
            is Map<*, *> -> variant["applicationId"] as? String ?: targetProject!!.group.toString()
            else -> variant.javaClass.getMethod("getApplicationId").invoke(variant) as? String
                ?: targetProject!!.group.toString()
        }
    }

    private fun getFlavorName(variant: Any): String {
        return when (variant) {
            is Map<*, *> -> variant["flavorName"] as? String ?: "default"
            else -> variant.javaClass.getMethod("getFlavorName").invoke(variant) as? String ?: "default"
        }
    }

    private fun getVariantName(variant: Any): String {
        return when (variant) {
            is Map<*, *> -> variant["name"] as? String ?: ""
            else -> variant.javaClass.getMethod("getName").invoke(variant) as? String ?: ""
        }
    }

    private fun generateChannelApkByConfigFile(
        configFile: File,
        apkFile: File,
        channelOutputFolder: File,
        nameVariantMap: MutableMap<String, Any>,
        extension: Extension
    ) {
        val config = Gson().fromJson(InputStreamReader(FileInputStream(configFile), "UTF-8"), WalleConfig::class.java)
        val defaultExtraInfo = config.defaultExtraInfo
        config.channelInfoList?.forEach { channelInfo ->
            var extraInfo = channelInfo.extraInfo
            if (!channelInfo.isExcludeDefaultExtraInfo) {
                when (config.defaultExtraInfoStrategy) {
                    WalleConfig.STRATEGY_IF_NONE -> {
                        if (extraInfo == null) {
                            extraInfo = defaultExtraInfo
                        }
                    }
                    WalleConfig.STRATEGY_ALWAYS -> {
                        val temp = mutableMapOf<String, String>()
                        defaultExtraInfo?.let { temp.putAll(it) }
                        extraInfo?.let { temp.putAll(it) }
                        extraInfo = temp
                    }
                }
            }
            generateChannelApk(apkFile, channelOutputFolder, nameVariantMap, channelInfo.channel, extraInfo, channelInfo.alias, extension)
        }
    }

    private fun generateChannelApkByChannelFile(
        channelFile: File,
        apkFile: File,
        channelOutputFolder: File,
        nameVariantMap: MutableMap<String, Any>,
        extension: Extension
    ) {
        getChannelListFromFile(channelFile).forEach { channel ->
            generateChannelApk(apkFile, channelOutputFolder, nameVariantMap, channel, null, null, extension)
        }
    }

    private fun generateChannelApk(
        apkFile: File,
        channelOutputFolder: File,
        nameVariantMap: MutableMap<String, Any>,
        channel: String?,
        extraInfo: Map<String, String>?,
        alias: String?,
        extension: Extension
    ) {
        val buildTime = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val channelName = alias ?: channel

        var fileName = apkFile.name
        if (fileName.endsWith(DOT_APK)) {
            fileName = fileName.substring(0, fileName.lastIndexOf(DOT_APK))
        }

        val apkFileName = "$fileName-$channelName$DOT_APK"

        val channelApkFile = File(channelOutputFolder, apkFileName)
        FileUtils.copyFile(apkFile, channelApkFile)
        ChannelWriter.put(channelApkFile, channel, extraInfo)

        nameVariantMap["buildTime"] = buildTime
        nameVariantMap["channel"] = channelName!!
        nameVariantMap["fileSHA1"] = getFileHash(channelApkFile)

        if (!extension.apkFileNameFormat.isNullOrEmpty()) {
            val newApkFileName = SimpleTemplateEngine()
                .createTemplate(extension.apkFileNameFormat)
                .make(nameVariantMap)
                .toString()
            if (newApkFileName != apkFileName) {
                channelApkFile.renameTo(File(channelOutputFolder, newApkFileName))
            }
        }
    }

    private fun createVariantAdapter(project: Project, variantName: String): Map<String, Any?> {
        // Get android extension
        val androidExt = project.extensions.findByName("android")
            ?: throw GradleException("Android extension not found")

        // Try to get variant from applicationVariants or libraryVariants
        var variants: Iterable<Any>? = null
        try {
            if (androidExt.javaClass.getMethod("getApplicationVariants").invoke(androidExt) != null) {
                variants = androidExt.javaClass.getMethod("getApplicationVariants").invoke(androidExt) as? Iterable<Any>
            }
        } catch (e: Exception) {
            try {
                if (androidExt.javaClass.getMethod("getLibraryVariants").invoke(androidExt) != null) {
                    variants = androidExt.javaClass.getMethod("getLibraryVariants").invoke(androidExt) as? Iterable<Any>
                }
            } catch (e2: Exception) {
                project.logger.warn("Could not access variants: ${e.message}")
            }
        }

        if (variants != null) {
            val foundVariant = variants.find { variant ->
                variant.javaClass.getMethod("getName").invoke(variant) == variantName
            }
            if (foundVariant != null) {
                return foundVariant as Map<String, Any?>
            }
        }

        // Fallback: create minimal mock variant using project properties
        val buildTypeName = if (variantName.contains("Release")) "release" else "debug"
        val flavorName = variantName.replace(buildTypeName.capitalize(), "").lowercase()

        val defaultConfig = androidExt.javaClass.getMethod("getDefaultConfig").invoke(androidExt)
        val versionName = defaultConfig.javaClass.getMethod("getVersionName").invoke(defaultConfig) as? String ?: "1.0"
        val versionCode = defaultConfig.javaClass.getMethod("getVersionCode").invoke(defaultConfig) as? Int ?: 1
        val applicationId = defaultConfig.javaClass.getMethod("getApplicationId").invoke(defaultConfig) as? String
            ?: project.group.toString()

        return mapOf(
            "name" to variantName,
            "buildType" to mapOf("name" to buildTypeName),
            "versionName" to versionName,
            "versionCode" to versionCode,
            "applicationId" to applicationId,
            "flavorName" to (flavorName.ifEmpty { "default" }),
            "outputs" to listOf(
                mapOf(
                    "outputFile" to File(
                        project.layout.buildDirectory.get().asFile,
                        "outputs/apk/$variantName/${project.name}-$buildTypeName.apk"
                    ),
                    "outputs" to listOf(
                        mapOf("filters" to emptyList<Map<String, String>>())
                    )
                )
            )
        )
    }
}
