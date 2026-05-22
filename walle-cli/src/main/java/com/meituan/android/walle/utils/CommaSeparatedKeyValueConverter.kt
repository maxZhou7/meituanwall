package com.meituan.android.walle.utils

import com.beust.jcommander.IStringConverter

class CommaSeparatedKeyValueConverter : IStringConverter<Map<String, String>> {
    override fun convert(value: String): Map<String, String>? {
        var result: MutableMap<String, String>? = null
        if (!Util.isTextEmpty(value)) {
            val temp = value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            result = HashMap(temp.size)
            for (s in temp) {
                val keyValue = s.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (keyValue.size == 2) {
                    result[keyValue[0]] = keyValue[1]
                }
            }
        }
        return result
    }
}
