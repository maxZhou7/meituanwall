package com.meituan.android.walle

import org.json.JSONException
import org.json.JSONObject
import java.io.File

object ChannelReader {
    const val CHANNEL_KEY = "channel"

    /**
     * easy api for get channel & extra info.<br/>
     *
     * @param apkFile apk file
     * @return null if not found
     */
    @JvmStatic
    fun get(apkFile: File): ChannelInfo? {
        val result = getMap(apkFile) ?: return null
        val channel = result[CHANNEL_KEY]
        val extraInfo = HashMap(result)
        extraInfo.remove(CHANNEL_KEY)
        return ChannelInfo(channel, extraInfo)
    }

    /**
     * get channel & extra info by map, use [ChannelReader.CHANNEL_KEY] get channel
     *
     * @param apkFile apk file
     * @return null if not found
     */
    @JvmStatic
    fun getMap(apkFile: File): Map<String, String>? {
        return try {
            val rawString = getRaw(apkFile) ?: return null
            val jsonObject = JSONObject(rawString)
            val keys = jsonObject.keys()
            val result = HashMap<String, String>()
            while (keys.hasNext()) {
                val key = keys.next().toString()
                result[key] = jsonObject.getString(key)
            }
            result
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * get raw string from channel id
     *
     * @param apkFile apk file
     * @return null if not found
     */
    @JvmStatic
    fun getRaw(apkFile: File): String? {
        return PayloadReader.getString(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID)
    }
}
