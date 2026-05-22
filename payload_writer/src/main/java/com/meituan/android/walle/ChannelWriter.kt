package com.meituan.android.walle

import org.json.JSONObject
import java.io.File
import java.io.IOException

object ChannelWriter {

    /**
     * write channel with channel fixed id
     *
     * @param apkFile apk file
     * @param channel channel
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, channel: String) {
        put(apkFile, channel, false)
    }

    /**
     * write channel with channel fixed id
     *
     * @param apkFile apk file
     * @param channel channel
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, channel: String, lowMemory: Boolean) {
        put(apkFile, channel, null, lowMemory)
    }

    /**
     * write channel & extra info with channel fixed id
     *
     * @param apkFile   apk file
     * @param channel   channel （nullable)
     * @param extraInfo extra info (don't use [ChannelReader.CHANNEL_KEY] as your key)
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, channel: String?, extraInfo: Map<String, String>?) {
        put(apkFile, channel, extraInfo, false)
    }

    /**
     * write channel & extra info with channel fixed id
     *
     * @param apkFile   apk file
     * @param channel   channel （nullable)
     * @param extraInfo extra info (don't use [ChannelReader.CHANNEL_KEY] as your key)
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, channel: String?, extraInfo: Map<String, String>?, lowMemory: Boolean) {
        val newData = HashMap<String, String>()
        val existsData = ChannelReader.getMap(apkFile)
        if (existsData != null) {
            newData.putAll(existsData)
        }
        if (extraInfo != null) {
            // can't use
            val extraInfoCopy = HashMap(extraInfo)
            extraInfoCopy.remove(ChannelReader.CHANNEL_KEY)
            newData.putAll(extraInfoCopy)
        }
        if (channel != null && channel.isNotEmpty()) {
            newData[ChannelReader.CHANNEL_KEY] = channel
        }
        val jsonObject = JSONObject(newData)
        putRaw(apkFile, jsonObject.toString(), lowMemory)
    }

    /**
     * write custom content with channel fixed id
     * NOTE: [ChannelReader.get] and [ChannelReader.getMap] may be affected
     *
     * @param apkFile apk file
     * @param string  custom content
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun putRaw(apkFile: File, string: String) {
        putRaw(apkFile, string, false)
    }

    /**
     * write custom content with channel fixed id
     * NOTE: [ChannelReader.get] and [ChannelReader.getMap] may be affected
     *
     * @param apkFile apk file
     * @param string  custom content
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun putRaw(apkFile: File, string: String, lowMemory: Boolean) {
        PayloadWriter.put(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID, string, lowMemory)
    }

    /**
     * remove channel id content
     *
     * @param apkFile apk file
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun remove(apkFile: File) {
        remove(apkFile, false)
    }

    /**
     * remove channel id content
     *
     * @param apkFile apk file
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun remove(apkFile: File, lowMemory: Boolean) {
        PayloadWriter.remove(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID, lowMemory)
    }
}
