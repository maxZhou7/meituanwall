package com.meituan.android.walle.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.meituan.android.walle.ChannelReader
import com.meituan.android.walle.utils.Fun1
import java.io.File

@Parameters(commandDescription = "get channel info from apk and show all by default")
class ShowCommand : IWalleCommand {

    @Parameter(required = true, description = "file1 file2 file3 ...", variableArity = true)
    private var filePaths: List<String>? = null

    @Parameter(names = ["-e", "--extraInfo"], description = "get channel extra info")
    private var showExtraInfo: Boolean = false

    @Parameter(names = ["-c", "--channel"], description = "get channel")
    private var showChannel: Boolean = false

    @Parameter(names = ["-r", "--raw"], description = "get raw string from Channel id")
    private var showRaw: Boolean = false

    override fun parse() {
        when {
            showRaw -> {
                printInfo(object : Fun1<File, String> {
                    override fun apply(file: File): String {
                        return ChannelReader.getRaw(file) ?: ""
                    }
                })
            }
            showExtraInfo -> {
                printInfo(object : Fun1<File, String> {
                    override fun apply(file: File): String {
                        val channelInfo = ChannelReader.get(file)
                        return channelInfo?.extraInfo?.toString() ?: ""
                    }
                })
                return
            }
            showChannel -> {
                printInfo(object : Fun1<File, String> {
                    override fun apply(file: File): String {
                        val channelInfo = ChannelReader.get(file)
                        return channelInfo?.channel ?: ""
                    }
                })
                return
            }
            else -> {
                printInfo(object : Fun1<File, String> {
                    override fun apply(file: File): String {
                        return ChannelReader.getMap(file)?.toString() ?: ""
                    }
                })
            }
        }
    }

    private fun printInfo(fun1: Fun1<File, String>) {
        filePaths?.map { File(it) }?.forEach { file ->
            println("${file.absolutePath} : ${fun1.apply(file)}")
        }
    }
}
