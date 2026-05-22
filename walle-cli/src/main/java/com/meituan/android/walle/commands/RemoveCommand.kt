package com.meituan.android.walle.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import com.meituan.android.walle.ChannelWriter
import com.meituan.android.walle.utils.Fun1
import java.io.File

@Parameters(commandDescription = "remove channel info for apk")
class RemoveCommand : IWalleCommand {

    @Parameter(required = true, description = "file1 file2 file3 ...", converter = FileConverter::class, variableArity = true)
    private var files: List<File>? = null

    override fun parse() {
        removeInfo(object : Fun1<File, Boolean> {
            override fun apply(file: File): Boolean {
                return try {
                    ChannelWriter.remove(file)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        })
    }

    private fun removeInfo(fun1: Fun1<File, Boolean>) {
        files?.forEach { file ->
            println("${file.absolutePath} : ${fun1.apply(file)}")
        }
    }
}
