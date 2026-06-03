package com.mobisec.omniip.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

object IPPacketBuilder {
    fun buildUdpResponse(
        sourceIp: InetAddress, destIp: InetAddress,
        sourcePort: Int, destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet)

        // IP Header
        buffer.put((0x45).toByte()) // Version 4, IHL 5
        buffer.put(0.toByte())      // TOS
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0)          // Identification
        buffer.putShort(0)          // Flags & Fragment Offset
        buffer.put(64.toByte())     // TTL
        buffer.put(17.toByte())     // Protocol: UDP
        buffer.putShort(0)          // Header Checksum (placeholder)
        buffer.put(sourceIp.address)
        buffer.put(destIp.address)

        // Calculate IP checksum
        buffer.putShort(10, calculateChecksum(packet, 0, 20))

        // UDP Header
        val udpLength = 8 + payload.size
        buffer.position(20)
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0) // UDP Checksum (placeholder)

        // Payload
        buffer.put(payload)

        // Calculate UDP checksum (including IPv4 pseudo-header)
        val pseudoHeader = ByteBuffer.allocate(12 + udpLength)
        pseudoHeader.put(sourceIp.address)
        pseudoHeader.put(destIp.address)
        pseudoHeader.put(0.toByte())
        pseudoHeader.put(17.toByte()) // Protocol UDP
        pseudoHeader.putShort(udpLength.toShort())
        pseudoHeader.put(packet, 20, udpLength)

        var udpChecksum = calculateChecksum(pseudoHeader.array(), 0, pseudoHeader.capacity())
        if (udpChecksum.toInt() == 0) {
            udpChecksum = 0xFFFF.toShort()
        }
        buffer.putShort(26, udpChecksum)

        return packet
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 != 0) {
            sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()).toShort()
    }
}
