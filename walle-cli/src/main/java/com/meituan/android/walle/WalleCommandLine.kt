package com.meituan.android.walle

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import java.io.IOException
import java.net.URL
import java.util.Enumeration
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

class WalleCommandLine {
    @Parameter(names = ["-v", "--version"], description = "show walle version")
    private var showVersion: Boolean = false

    @Parameter(names = ["-h", "--help"], description = "show walle command line help")
    private var showHelp: Boolean = false

    fun parse(commander: JCommander) {
        if (showVersion) {
            println(getVersion())
            return
        }
        if (showHelp) {
            commander.usage()
        }
    }

    companion object {
        private fun getVersion(): String? {
            try {
                val resEnum: Enumeration<URL> = Thread.currentThread().contextClassLoader.getResources(JarFile.MANIFEST_NAME)
                while (resEnum.hasMoreElements()) {
                    try {
                        val url = resEnum.nextElement()
                        val inputStream = url.openStream()
                        if (inputStream != null) {
                            val manifest = Manifest(inputStream)
                            val mainAttribs: Attributes = manifest.mainAttributes
                            val version = mainAttribs.getValue("Walle-Version")
                            if (version != null) {
                                return version
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }
}
