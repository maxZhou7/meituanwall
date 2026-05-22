package com.meituan.android.walle.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import com.meituan.android.walle.ChannelWriter
import com.meituan.android.walle.utils.CommaSeparatedKeyValueConverter
import com.meituan.android.walle.utils.Util
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException

@Parameters(commandDescription = "channel apk batch production")
class BatchCommand : IWalleCommand {

    @Parameter(required = true, description = "inputFile [outputDirectory]", arity = 2, converter = FileConverter::class)
    private var files: List<File>? = null

    @Parameter(names = ["-e", "--extraInfo"], converter = CommaSeparatedKeyValueConverter::class, description = "Comma-separated list of key=value info, eg: -e time=1,type=android")
    private var extraInfo: Map<String, String>? = null

    @Parameter(names = ["-c", "--channelList"], description = "Comma-separated list of channel, eg: -c meituan,xiaomi")
    private var channelList: List<String>? = null

    @Parameter(names = ["-f", "--channelFile"], description = "channel file")
    private var channelFile: File? = null

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

        channelList?.forEach { channel ->
            generateChannelApk(inputFile, outputDir, channel)
        }

        channelFile?.let { file ->
            try {
                val lines = IOUtils.readLines(FileInputStream(file), "UTF-8")
                for (line in lines) {
                    val lineTrim = line.trim()
                    if (lineTrim.isEmpty() || lineTrim.startsWith("#")) {
                        continue
                    }
                    val channel = line.split("#")[0].trim()
                    if (channel.isNotEmpty()) {
                        generateChannelApk(inputFile, outputDir, channel)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun generateChannelApk(inputFile: File, outputDir: File, channel: String) {
        val name = FilenameUtils.getBaseName(inputFile.name)
        val extension = FilenameUtils.getExtension(inputFile.name)
        val newName = "${name}_${channel}.${extension}"
        val channelApk = File(outputDir, newName)
        try {
            FileUtils.copyFile(inputFile, channelApk)
            ChannelWriter.put(channelApk, channel, extraInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
