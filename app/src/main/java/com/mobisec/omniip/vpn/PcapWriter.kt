package com.mobisec.omniip.vpn

import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapWriter(private val pfd: ParcelFileDescriptor) {
    private var outputStream: FileOutputStream? = null

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            outputStream = FileOutputStream(pfd.fileDescriptor)
            writeGlobalHeader()
        }
    }

    private fun writeGlobalHeader() {
        val header = ByteBuffer.allocate(24)
        header.order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0xa1b2c3d4.toInt()) // Magic number
        header.putShort(2) // Major version
        header.putShort(4) // Minor version
        header.putInt(0) // Reserved1
        header.putInt(0) // Reserved2
        header.putInt(65535) // Snaplen
        header.putInt(101) // Linktype (101 = LINKTYPE_RAW for IPv4/IPv6)

        outputStream?.write(header.array())
        outputStream?.flush()
    }

    suspend fun writePacket(packetData: ByteArray, length: Int) {
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val tsSec = (now / 1000).toInt()
                val tsUsec = ((now % 1000) * 1000).toInt()

                val packetHeader = ByteBuffer.allocate(16)
                packetHeader.order(ByteOrder.LITTLE_ENDIAN)
                packetHeader.putInt(tsSec)
                packetHeader.putInt(tsUsec)
                packetHeader.putInt(length)
                packetHeader.putInt(length)

                outputStream?.write(packetHeader.array())
                outputStream?.write(packetData, 0, length)
                outputStream?.flush()
            } catch (e: Exception) {
                // Ignore IOException during stream close
            }
        }
    }

    fun getFd(): Int = pfd.fd

    suspend fun close() {
        withContext(Dispatchers.IO) {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                pfd.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
