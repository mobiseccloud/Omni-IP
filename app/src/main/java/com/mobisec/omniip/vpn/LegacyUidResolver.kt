package com.mobisec.omniip.vpn

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object LegacyUidResolver {

    fun resolveUid(protocol: Int, sourcePort: Int): Int {
        val path = if (protocol == 6) "/proc/net/tcp" else "/proc/net/udp"
        val hexPort = String.format("%04X", sourcePort)

        try {
            val file = File(path)
            if (!file.exists()) return -1

            BufferedReader(FileReader(file)).use { reader ->
                var line = reader.readLine() // skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line.trim().split(Regex("\\s+"))
                    if (tokens.size >= 8) {
                        val localAddress = tokens[1]
                        val parts = localAddress.split(":")
                        if (parts.size == 2 && parts[1] == hexPort) {
                            return tokens[7].toIntOrNull() ?: -1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return -1
    }
}
