package com.mobisec.omniip.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

object PacketUtils {
    fun getProtocol(packet: ByteBuffer): Int {
        return packet.get(9).toInt() and 0xFF
    }

    fun getSourceIP(packet: ByteBuffer): InetAddress {
        val ip = ByteArray(4)
        packet.position(12)
        packet.get(ip)
        return InetAddress.getByAddress(ip)
    }

    fun getDestIP(packet: ByteBuffer): InetAddress {
        val ip = ByteArray(4)
        packet.position(16)
        packet.get(ip)
        return InetAddress.getByAddress(ip)
    }

    fun getIHL(packet: ByteBuffer): Int {
        return (packet.get(0).toInt() and 0x0F) * 4
    }

    fun getSourcePort(packet: ByteBuffer, ihl: Int): Int {
        return packet.getShort(ihl).toInt() and 0xFFFF
    }

    fun getDestPort(packet: ByteBuffer, ihl: Int): Int {
        return packet.getShort(ihl + 2).toInt() and 0xFFFF
    }
}
