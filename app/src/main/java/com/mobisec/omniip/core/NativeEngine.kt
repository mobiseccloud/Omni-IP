package com.mobisec.omniip.core

object NativeEngine {
    init {
        System.loadLibrary("omniip_bridge")
    }

    external fun executeNmapScan(target: String): String
    external fun executeRawPing(target: String): String
    external fun executeLanSweep(subnet: String): String
    external fun executeTraceroute(target: String): String
    external fun initializeNativeEnvironment(isDebug: Boolean)
    external fun setPremiumUnlockedNative(unlocked: Boolean)
    external fun executeSecuritySweep(context: android.content.Context)
    external fun processPacketNative(packetBuffer: java.nio.ByteBuffer, length: Int): Int

    external fun updateNativeRule(key: String, ip: Int, port: Int, action: Int)
    external fun clearNativeRules()
    external fun syncThreatBloomFilter(bitArray: LongArray, hashCount: Int)
    external fun syncDnsBlocklist(domainHashes: LongArray)
}
