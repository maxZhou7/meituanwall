package com.meituan.android.walle.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import com.meituan.android.walle.ChannelWriter
import com.meituan.android.walle.utils.CommaSeparatedKeyValueConverter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.io.File

@Parameters(commandDescription = "put channel info into apk")
class PutCommand : IWalleCommand {

    @Parameter(required = true, description = "inputFile [outputFile]", arity = 2, converter = FileConverter::class)
    private var files: List<File>? = null

    @Parameter(names = ["-e", "--extraInfo"], converter = CommaSeparatedKeyValueConverter::class, description = "Comma-separated list of key=value info, eg: -e time=1,type=android")
    private var extraInfo: Map<String, String>? = null

    @Parameter(names = ["-c", "--channel"], description = "single channel, eg: -c meituan")
    private var channel: String? = null

    override fun parse() {
        val inputFile = files!![0]
        val outputFile: File = if (files!!.size == 2) {
            files!![1]
        } else {
            val name = FilenameUtils.getBaseName(inputFile.name)
            val extension = FilenameUtils.getExtension(inputFile.name)
            val newName = "${name}_${channel}.${extension}"
            File(inputFile.parent, newName)
        }
        if (inputFile == outputFile) {
            try {
                ChannelWriter.put(outputFile, channel, extraInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                FileUtils.copyFile(inputFile, outputFile)
                ChannelWriter.put(outputFile, channel, extraInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
