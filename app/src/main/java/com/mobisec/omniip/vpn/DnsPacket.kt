package com.mobisec.omniip.vpn

import java.nio.ByteBuffer

class DnsPacket(val rawData: ByteArray, val offset: Int, val length: Int) {
    var domain: String? = null
    var transactionId: Short = 0
    var isResponse: Boolean = false
    var answerCount: Short = 0

    init {
        parse()
    }

    private fun parse() {
        if (length < 12) return // Invalid DNS header

        val buffer = ByteBuffer.wrap(rawData, offset, length)
        transactionId = buffer.short
        val flags = buffer.short.toInt()
        isResponse = (flags and 0x8000) != 0

        val questionCount = buffer.short
        answerCount = buffer.short
        val authorityCount = buffer.short
        val additionalCount = buffer.short

        if (questionCount > 0) {
            domain = parseDomainName(buffer)
        }
    }

    private fun parseDomainName(buffer: ByteBuffer): String {
        val sb = StringBuilder()
        var len = buffer.get().toInt() and 0xFF
        while (len > 0) {
            if ((len and 0xC0) == 0xC0) {
                // Pointer - just skip for basic parsing of queries
                buffer.get() // Skip second byte of pointer
                break
            } else {
                val bytes = ByteArray(len)
                buffer.get(bytes)
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(String(bytes))
                len = buffer.get().toInt() and 0xFF
            }
        }
        return sb.toString()
    }

    fun getResolvedIps(): List<String> {
        val ips = mutableListOf<String>()
        if (!isResponse || answerCount <= 0) return ips

        val buffer = ByteBuffer.wrap(rawData, offset, length)
        buffer.position(offset + 12) // Skip header

        // Skip queries
        parseDomainName(buffer)
        buffer.short // type
        buffer.short // class

        // Parse answers
        for (i in 0 until answerCount) {
            if (buffer.remaining() < 12) break

            // Name
            val nameType = buffer.get().toInt() and 0xFF
            if ((nameType and 0xC0) == 0xC0) {
                buffer.get() // Pointer
            } else {
                buffer.position(buffer.position() - 1)
                parseDomainName(buffer)
            }

            val type = buffer.short.toInt() and 0xFFFF
            buffer.short // class
            buffer.int // ttl
            val dataLen = buffer.short.toInt() and 0xFFFF

            if (type == 1 && dataLen == 4) { // A record (IPv4)
                val ip = ByteArray(4)
                buffer.get(ip)
                ips.add("${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}")
            } else {
                buffer.position(buffer.position() + dataLen)
            }
        }

        return ips
    }
}
