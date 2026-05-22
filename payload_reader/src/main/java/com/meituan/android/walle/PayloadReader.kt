package com.meituan.android.walle

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

object PayloadReader {
    /**
     * get string (UTF-8) by id
     *
     * @param apkFile apk file
     * @return null if not found
     */
    @JvmStatic
    fun getString(apkFile: File, id: Int): String? {
        val bytes = get(apkFile, id) ?: return null
        return try {
            String(bytes, Charset.forName(ApkUtil.DEFAULT_CHARSET))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * get bytes by id <br/>
     *
     * @param apkFile apk file
     * @param id      id
     * @return bytes
     */
    @JvmStatic
    fun get(apkFile: File, id: Int): ByteArray? {
        val idValues = getAll(apkFile) ?: return null
        val byteBuffer = idValues[id] ?: return null
        return getBytes(byteBuffer)
    }

    /**
     * get data from byteBuffer
     *
     * @param byteBuffer buffer
     * @return useful data
     */
    private fun getBytes(byteBuffer: ByteBuffer): ByteArray {
        val array = byteBuffer.array()
        val arrayOffset = byteBuffer.arrayOffset()
        return array.copyOfRange(arrayOffset + byteBuffer.position(), arrayOffset + byteBuffer.limit())
    }

    /**
     * get all custom (id, buffer) <br/>
     * Note: get final from byteBuffer, please use [PayloadReader.getBytes]
     *
     * @param apkFile apk file
     * @return all custom (id, buffer)
     */
    private fun getAll(apkFile: File): Map<Int, ByteBuffer>? {
        var idValues: Map<Int, ByteBuffer>? = null
        try {
            var randomAccessFile: RandomAccessFile? = null
            var fileChannel: FileChannel? = null
            try {
                randomAccessFile = RandomAccessFile(apkFile, "r")
                fileChannel = randomAccessFile.channel
                val apkSigningBlock2 = ApkUtil.findApkSigningBlock(fileChannel).getFirst()
                idValues = ApkUtil.findIdValues(apkSigningBlock2)
            } catch (ignore: IOException) {
            } finally {
                try {
                    fileChannel?.close()
                } catch (ignore: IOException) {
                }
                try {
                    randomAccessFile?.close()
                } catch (ignore: IOException) {
                }
            }
        } catch (ignore: SignatureNotFoundException) {
        }

        return idValues
    }
}
