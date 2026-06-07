package com.mobisec.omniip.vpn

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapWriter(private val pfd: ParcelFileDescriptor) {
    private var outputStream: FileOutputStream? = null
    private val packetChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeJob: Job? = null

    suspend fun initialize(scope: CoroutineScope) {
        withContext(Dispatchers.IO) {
            outputStream = FileOutputStream(pfd.fileDescriptor)
            writeGlobalHeader()
        }
        writeJob = scope.launch(Dispatchers.IO) {
            for (packetData in packetChannel) {
                try {
                    val now = System.currentTimeMillis()
                    val tsSec = (now / 1000).toInt()
                    val tsUsec = ((now % 1000) * 1000).toInt()
                    
                    val length = packetData.size

                    val packetHeader = ByteBuffer.allocate(16)
                    packetHeader.order(ByteOrder.LITTLE_ENDIAN)
                    packetHeader.putInt(tsSec)
                    packetHeader.putInt(tsUsec)
                    packetHeader.putInt(length)
                    packetHeader.putInt(length)

                    outputStream?.write(packetHeader.array())
                    outputStream?.write(packetData)
                    outputStream?.flush()
                } catch (e: Exception) {
                    // Ignore IOException
                }
            }
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

    fun writePacket(packetData: ByteArray, length: Int) {
        val copy = packetData.copyOfRange(0, length)
        packetChannel.trySend(copy)
    }

    fun getFd(): Int = pfd.fd

    suspend fun close() {
        packetChannel.close()
        writeJob?.join()
        withContext(Dispatchers.IO) {
            try {
                outputStream?.flush() // Ensure all buffered packets are flushed to disk
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
