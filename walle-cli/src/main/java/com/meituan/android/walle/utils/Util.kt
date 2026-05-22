package com.meituan.android.walle.utils

import java.io.File

object Util {
    @JvmStatic
    fun isTextEmpty(text: String?): Boolean {
        return text == null || text.isEmpty()
    }

    @JvmStatic
    fun removeDirInvalidChar(file: File): File {
        if (System.getProperties().getProperty("os.name").uppercase().startsWith("WINDOWS")) {
            val newFileName = file.name.replace("\"".toRegex(), "")
            return File(file.parent, newFileName)
        }
        return file
    }
}
