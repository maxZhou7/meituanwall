package com.meituan.android.walle

import org.gradle.api.Project
import java.io.File

open class Extension(project: Project) {

    companion object {
        const val DEFAULT_APK_FILE_NAME_TEMPLATE = "\${appName}-\${buildType}-\${channel}.apk"

        @JvmStatic
        fun getConfig(project: Project): Extension {
            var config = project.extensions.findByType(Extension::class.java)
            if (config == null) {
                config = Extension(project)
            }
            return config
        }
    }

    /**
     *  apk output dir
     *  default value: null, the channels' apk will output in '\${project}/build/output/apk' folder
     */
    var apkOutputFolder: File? = null

    /**
     * file name template string
     *
     * Available vars:
     * 1. projectName
     * 2. appName
     * 3. packageName
     * 4. buildType
     * 5. channel
     * 6. versionName
     * 7. versionCode
     * 8. buildTime
     * 9. fileSHA1
     * 10. flavorName
     *
     * default value: '\${appName}-\${buildType}-\${channel}.apk'
     *
     */
    var apkFileNameFormat: String = DEFAULT_APK_FILE_NAME_TEMPLATE

    /**
     * only channel
     */
    var channelFile: File? = null

    /**
     * channel & extraInfo config
     */
    var configFile: File? = null

    /**
     * Config file name.
     * Will find the file in the following locations:
     * /src/{variantName}/
     * /src/{flavor}/
     * /src/{buildType}/
     * /src/main/
     */
    var variantConfigFileName: String? = null

    init {
        apkOutputFolder = null
        apkFileNameFormat = DEFAULT_APK_FILE_NAME_TEMPLATE
    }
}
