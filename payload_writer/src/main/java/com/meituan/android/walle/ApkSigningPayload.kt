package com.meituan.android.walle

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

internal class ApkSigningPayload(
    val id: Int,
    private val buffer: ByteBuffer
) {
    /**
     * Total bytes of this block
     */
    val totalSize: Int

    init {
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw IllegalArgumentException("ByteBuffer byte order must be little endian")
        }
        // assume buffer is not consumed
        totalSize = 8 + 4 + buffer.remaining() // size + id + value
    }

    val byteBuffer: ByteArray
        get() {
            val array = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            return Arrays.copyOfRange(
                array, arrayOffset + buffer.position(),
                arrayOffset + buffer.limit()
            )
        }
}
