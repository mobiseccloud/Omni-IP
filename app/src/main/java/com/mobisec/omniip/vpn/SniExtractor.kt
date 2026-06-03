package com.mobisec.omniip.vpn

import java.nio.ByteBuffer

object SniExtractor {
    fun getSni(packet: ByteBuffer, ihl: Int): String? {
        val oldPosition = packet.position()
        try {
            val dataOffsetAndFlags = packet.getShort(ihl + 12).toInt()
            val tcpHeaderLength = ((dataOffsetAndFlags ushr 12) and 0x0F) * 4

            val payloadOffset = ihl + tcpHeaderLength
            val payloadLength = packet.limit() - payloadOffset

            if (payloadLength < 43) {
                return null
            }

            packet.position(payloadOffset)

            // TLS ContentType: Handshake (22)
            if (packet.get().toInt() != 22) return null
            // Version: TLS 1.0 - TLS 1.2 (0x0301 - 0x0303)
            val majorVersion = packet.get().toInt() and 0xFF
            val minorVersion = packet.get().toInt() and 0xFF
            if (majorVersion != 3) return null

            // Length
            val tlsLength = packet.short.toInt() and 0xFFFF
            if (tlsLength > payloadLength - 5) return null

            // HandshakeType: ClientHello (1)
            if (packet.get().toInt() != 1) return null

            // Handshake Length (3 bytes)
            packet.get()
            packet.get()
            packet.get()

            // Handshake Version
            packet.short

            // Random (32 bytes)
            packet.position(packet.position() + 32)

            // Session ID Length
            val sessionIdLen = packet.get().toInt() and 0xFF
            packet.position(packet.position() + sessionIdLen)

            // Cipher Suites Length
            val cipherSuitesLen = packet.short.toInt() and 0xFFFF
            packet.position(packet.position() + cipherSuitesLen)

            // Compression Methods Length
            val compMethodsLen = packet.get().toInt() and 0xFF
            packet.position(packet.position() + compMethodsLen)

            // Extensions Length
            if (packet.remaining() < 2) return null
            val extensionsLen = packet.short.toInt() and 0xFFFF
            val extensionsEnd = packet.position() + extensionsLen

            while (packet.position() < extensionsEnd && packet.position() < packet.limit() - 4) {
                val extType = packet.short.toInt() and 0xFFFF
                val extLen = packet.short.toInt() and 0xFFFF

                if (extType == 0) { // Server Name Indication
                    if (packet.remaining() < 2) return null
                    val snListLen = packet.short.toInt() and 0xFFFF
                    if (snListLen > 0) {
                        val snType = packet.get().toInt() and 0xFF
                        if (snType == 0) { // host_name
                            val snLen = packet.short.toInt() and 0xFFFF
                            val snBytes = ByteArray(snLen)
                            packet.get(snBytes)
                            return String(snBytes)
                        }
                    }
                    break
                } else {
                    packet.position(packet.position() + extLen)
                }
            }
        } catch (e: Exception) {
            // Ignore malformed packets
        } finally {
            packet.position(oldPosition)
        }

        return null
    }
}
