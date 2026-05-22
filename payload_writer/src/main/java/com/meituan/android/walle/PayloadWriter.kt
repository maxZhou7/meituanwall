package com.meituan.android.walle

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.UUID

object PayloadWriter {

    /**
     * put (id, String) into apk, update if id exists
     * @param apkFile apk file
     * @param id id
     * @param string string content
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, id: Int, string: String) {
        put(apkFile, id, string, false)
    }

    /**
     * put (id, String) into apk, update if id exists
     * @param apkFile apk file
     * @param id id
     * @param string string
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, id: Int, string: String, lowMemory: Boolean) {
        val bytes = string.toByteArray(Charsets.UTF_8)
        val byteBuffer = ByteBuffer.allocate(bytes.size)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.put(bytes, 0, bytes.size)
        byteBuffer.flip()
        put(apkFile, id, byteBuffer, lowMemory)
    }

    /**
     * put (id, buffer) into apk, update if id exists
     *
     * @param apkFile apk file
     * @param id      id
     * @param buffer  buffer
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, id: Int, buffer: ByteBuffer) {
        put(apkFile, id, buffer, false)
    }

    /**
     * put (id, buffer) into apk, update if id exists
     * @param apkFile apk file
     * @param id id
     * @param buffer buffer
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun put(apkFile: File, id: Int, buffer: ByteBuffer, lowMemory: Boolean) {
        val idValues = HashMap<Int, ByteBuffer>()
        idValues[id] = buffer
        putAll(apkFile, idValues, lowMemory)
    }

    /**
     * put new idValues into apk, update if id exists
     *
     * @param apkFile  apk file
     * @param idValues id value. NOTE: use unknown IDs. DO NOT use ID that have already been used.  See [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun putAll(apkFile: File, idValues: Map<Int, ByteBuffer>) {
        putAll(apkFile, idValues, false)
    }

    /**
     * put new idValues into apk, update if id exists
     *
     * @param apkFile  apk file
     * @param idValues id value. NOTE: use unknown IDs. DO NOT use ID that have already been used.  See [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun putAll(apkFile: File, idValues: Map<Int, ByteBuffer>, lowMemory: Boolean) {
        handleApkSigningBlock(apkFile, object : ApkSigningBlockHandler {
            override fun handle(originIdValues: MutableMap<Int, ByteBuffer>): ApkSigningBlock {
                if (idValues.isNotEmpty()) {
                    originIdValues.putAll(idValues)
                }
                val apkSigningBlock = ApkSigningBlock()
                val entrySet = originIdValues.entries
                for ((key, value) in entrySet) {
                    val payload = ApkSigningPayload(key, value)
                    apkSigningBlock.addPayload(payload)
                }
                return apkSigningBlock
            }
        }, lowMemory)
    }

    /**
     * remove content by id
     *
     * @param apkFile apk file
     * @param id id
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun remove(apkFile: File, id: Int) {
        remove(apkFile, id, false)
    }

    /**
     * remove content by id
     *
     * @param apkFile apk file
     * @param id id
     * @param lowMemory  if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    @JvmStatic
    @Throws(IOException::class, SignatureNotFoundException::class)
    fun remove(apkFile: File, id: Int, lowMemory: Boolean) {
        handleApkSigningBlock(apkFile, object : ApkSigningBlockHandler {
            override fun handle(originIdValues: MutableMap<Int, ByteBuffer>): ApkSigningBlock {
                val apkSigningBlock = ApkSigningBlock()
                val entrySet = originIdValues.entries
                for ((key, value) in entrySet) {
                    if (key != id) {
                        val payload = ApkSigningPayload(key, value)
                        apkSigningBlock.addPayload(payload)
                    }
                }
                return apkSigningBlock
            }
        }, lowMemory)
    }

    internal interface ApkSigningBlockHandler {
        fun handle(originIdValues: MutableMap<Int, ByteBuffer>): ApkSigningBlock
    }

    @Throws(IOException::class, SignatureNotFoundException::class)
    internal fun handleApkSigningBlock(apkFile: File, handler: ApkSigningBlockHandler, lowMemory: Boolean) {
        var fIn: RandomAccessFile? = null
        var fileChannel: FileChannel? = null
        try {
            fIn = RandomAccessFile(apkFile, "rw")
            fileChannel = fIn.channel
            val commentLength = ApkUtil.getCommentLength(fileChannel)
            val centralDirStartOffset = ApkUtil.findCentralDirStartOffset(fileChannel, commentLength)
            // Find the APK Signing Block. The block immediately precedes the Central Directory.
            val apkSigningBlockAndOffset = ApkUtil.findApkSigningBlock(fileChannel, centralDirStartOffset)
            val apkSigningBlock2 = apkSigningBlockAndOffset.first
            val apkSigningBlockOffset = apkSigningBlockAndOffset.second

            val originIdValues = ApkUtil.findIdValues(apkSigningBlock2)
            // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
            val apkSignatureSchemeV2Block = originIdValues[ApkUtil.APK_SIGNATURE_SCHEME_V2_BLOCK_ID]

            if (apkSignatureSchemeV2Block == null) {
                throw IOException("No APK Signature Scheme v2 block in APK Signing Block")
            }

            val needPadding = originIdValues.remove(ApkUtil.VERITY_PADDING_BLOCK_ID) != null
            val apkSigningBlock = handler.handle(originIdValues)
            // replace VERITY_PADDING_BLOCK with new one
            if (needPadding) {
                // uint64:  size (excluding this field)
                // repeated ID-value pairs:
                //     uint64:           size (excluding this field)
                //     uint32:           ID
                //     (size - 4) bytes: value
                // (extra dummy ID-value for padding to make block size a multiple of 4096 bytes)
                // uint64:  size (same as the one above)
                // uint128: magic

                var blocksSize = 0
                for (payload in apkSigningBlock.payloads) {
                    blocksSize += payload.totalSize
                }

                val resultSize = 8 + blocksSize + 8 + 16 // size(uint64) + pairs size + size(uint64) + magic(uint128)
                if (resultSize % ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES != 0) {
                    var padding = (ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES - 12 // size(uint64) + id(uint32)
                            - (resultSize % ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES))
                    if (padding < 0) {
                        padding += ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES
                    }
                    val dummy = ByteBuffer.allocate(padding).order(ByteOrder.LITTLE_ENDIAN)
                    apkSigningBlock.addPayload(ApkSigningPayload(ApkUtil.VERITY_PADDING_BLOCK_ID, dummy))
                }
            }

            if (apkSigningBlockOffset != 0L && centralDirStartOffset != 0L) {

                // read CentralDir
                fIn.seek(centralDirStartOffset)

                var centralDirBytes: ByteArray? = null
                var tempCentralBytesFile: File? = null
                // read CentralDir
                if (lowMemory) {
                    tempCentralBytesFile = File(apkFile.parent, UUID.randomUUID().toString())
                    var outStream: FileOutputStream? = null
                    try {
                        outStream = FileOutputStream(tempCentralBytesFile)
                        val buffer = ByteArray(1024)

                        var len: Int
                        while (fIn.read(buffer).also { len = it } > 0) {
                            outStream.write(buffer, 0, len)
                        }
                    } finally {
                        outStream?.close()
                    }
                } else {
                    centralDirBytes = ByteArray((fileChannel.size() - centralDirStartOffset).toInt())
                    fIn.read(centralDirBytes)
                }

                //update apk sign
                fileChannel.position(apkSigningBlockOffset)
                val length = apkSigningBlock.writeApkSigningBlock(fIn)

                // update CentralDir
                if (lowMemory) {
                    var inputStream: FileInputStream? = null
                    try {
                        inputStream = FileInputStream(tempCentralBytesFile)
                        val buffer = ByteArray(1024)

                        var len: Int
                        while (inputStream.read(buffer).also { len = it } > 0) {
                            fIn.write(buffer, 0, len)
                        }
                    } finally {
                        inputStream?.close()
                        tempCentralBytesFile?.delete()
                    }
                } else {
                    // store CentralDir
                    fIn.write(centralDirBytes)
                }
                // update length
                fIn.setLength(fIn.filePointer)

                // update CentralDir Offset

                // End of central directory record (EOCD)
                // Offset     Bytes     Description[23]
                // 0            4       End of central directory signature = 0x06054b50
                // 4            2       Number of this disk
                // 6            2       Disk where central directory starts
                // 8            2       Number of central directory records on this disk
                // 10           2       Total number of central directory records
                // 12           4       Size of central directory (bytes)
                // 16           4       Offset of start of central directory, relative to start of archive
                // 20           2       Comment length (n)
                // 22           n       Comment

                fIn.seek(fileChannel.size() - commentLength - 6)
                // 6 = 2(Comment length) + 4 (Offset of start of central directory, relative to start of archive)
                val temp = ByteBuffer.allocate(4)
                temp.order(ByteOrder.LITTLE_ENDIAN)
                temp.putInt((centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset)).toInt())
                // 8 = size of block in bytes (excluding this field) (uint64)
                temp.flip()
                fIn.write(temp.array())
            }
        } finally {
            fileChannel?.close()
            fIn?.close()
        }
    }
}
