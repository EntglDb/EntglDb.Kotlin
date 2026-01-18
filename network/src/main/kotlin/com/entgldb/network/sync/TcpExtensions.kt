package com.entgldb.network.sync

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun OutputStream.writeIntLe(value: Int) {
    val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    write(buffer)
}

fun InputStream.readIntLe(): Int {
    val buffer = ByteArray(4)
    var total = 0
    while (total < 4) {
        val read = read(buffer, total, 4 - total)
        if (read == -1) throw java.io.EOFException()
        total += read
    }
    return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).int
}

fun InputStream.readFully(buffer: ByteArray) {
    var total = 0
    while (total < buffer.size) {
        val read = read(buffer, total, buffer.size - total)
        if (read == -1) throw java.io.EOFException()
        total += read
    }
}
