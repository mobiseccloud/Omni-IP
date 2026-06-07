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
    external fun processPacketNative(packetBuffer: java.nio.ByteBuffer, length: Int, pcapFd: Int): Int
    external fun startLwipProxy(vpnFd: Int)

        var telemetryCallback: ((Int, String, Int, Int, Int, Int) -> Unit)? = null
    @JvmStatic fun onTelemetryEvent(sourcePort: Int, destIp: String, destPort: Int, protocol: Int, txBytes: Int, rxBytes: Int) {
        telemetryCallback?.invoke(sourcePort, destIp, destPort, protocol, txBytes, rxBytes)
    }

    var socketProtector: ((Int) -> Boolean)? = null
    @JvmStatic fun protectSocket(fd: Int): Boolean {
        return socketProtector?.invoke(fd) ?: false
    }

    external fun updateNativeRule(key: String, ip: Int, port: Int, action: Int)
    external fun updateNativeAppRule(uid: Int, direction: Int, interfaceType: Int, isBlocked: Boolean)
    external fun updateGeoRule(countryCode: String, action: String)
    external fun updateRulesBulk(keys: LongArray, txActions: ByteArray, rxActions: ByteArray)

    fun syncRulesToNative(rules: List<com.mobisec.omniip.db.FirewallRule>) {
        val size = rules.size
        val keys = LongArray(size)
        val txActions = ByteArray(size)
        val rxActions = ByteArray(size)

        for (i in rules.indices) {
            val rule = rules[i]
            val targetStr = rule.targetValue
            
            // Unified 64-bit hashing for all target types
            keys[i] = HashUtils.murmurHash3(targetStr)

            // Map symmetric actions (Legacy Support)
            when (rule.action) {
                com.mobisec.omniip.db.Action.BLOCK -> {
                    txActions[i] = 0 // DROP
                    rxActions[i] = 0 // DROP
                }
                com.mobisec.omniip.db.Action.IGNORE -> {
                    txActions[i] = 1 // ALLOW
                    rxActions[i] = 1 // ALLOW
                }
                com.mobisec.omniip.db.Action.FLAG -> {
                    txActions[i] = 2 // FLAG
                    rxActions[i] = 2 // FLAG
                }
            }
        }
        
        updateRulesBulk(keys, txActions, rxActions)
    }

    external fun clearNativeRules()
    external fun syncThreatBloomFilter(bitArray: LongArray, hashCount: Int)
    external fun syncDnsBlocklist(domainHashes: LongArray)
}
