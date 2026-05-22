package com.meituan.android.walle.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import com.google.gson.Gson
import com.meituan.android.walle.ChannelWriter
import com.meituan.android.walle.WalleConfig
import com.meituan.android.walle.utils.Util
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

@Parameters(commandDescription = "channel apk batch production")
class Batch2Command : IWalleCommand {

    @Parameter(required = true, description = "inputFile [outputDirectory]", arity = 2, converter = FileConverter::class)
    private var files: List<File>? = null

    @Parameter(names = ["-f", "--configFile"], description = "config file (json)")
    private var configFile: File? = null

    override fun parse() {
        val inputFile = files!![0]
        val outputDir: File = if (files!!.size == 2) {
            Util.removeDirInvalidChar(files!![1]).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        } else {
            inputFile.parentFile
        }

        configFile?.let { file ->
            try {
                val config = Gson().fromJson(InputStreamReader(FileInputStream(file), "UTF-8"), WalleConfig::class.java)
                val defaultExtraInfo = config.defaultExtraInfo
                val channelInfoList = config.channelInfoList
                channelInfoList?.forEach { channelInfo ->
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
                    generateChannelApk(inputFile, outputDir, channelInfo.channel, channelInfo.alias, extraInfo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateChannelApk(inputFile: File, outputDir: File, channel: String?, alias: String?, extraInfo: Map<String, String>?) {
        val channelName = alias ?: channel
        val name = FilenameUtils.getBaseName(inputFile.name)
        val extension = FilenameUtils.getExtension(inputFile.name)
        val newName = "${name}_${channelName}.${extension}"
        val channelApk = File(outputDir, newName)
        try {
            FileUtils.copyFile(inputFile, channelApk)
            ChannelWriter.put(channelApk, channel, extraInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
