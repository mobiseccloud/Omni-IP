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
    external fun passToLwip(packetBuffer: java.nio.ByteBuffer, length: Int)
    external fun killTcpSession(sourceIp: ByteArray, destIp: ByteArray, sourcePort: Int, destPort: Int)
    external fun setShizukuPrivileges(isGranted: Boolean)
    external fun setRawIcmpSocket(fd: Int)

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

    private fun ipToLong(ipAddress: String): Long {
        try {
            val parts = ipAddress.split(".")
            if (parts.size == 4) {
                var num = 0L
                for (i in 3 downTo 0) {
                    val ip = parts[3 - i].toLong()
                    num = num or (ip shl (i * 8))
                }
                return num
            }
        } catch (e: Exception) {
            // Fallback
        }
        return 0L
    }

    fun syncRulesToNative(rules: List<com.mobisec.omniip.db.FirewallRule>) {
        val size = rules.size
        val keys = LongArray(size)
        val txActions = ByteArray(size)
        val rxActions = ByteArray(size)

        for (i in rules.indices) {
            val rule = rules[i]
            val targetStr = rule.targetValue
            
            var baseKey: Long = when (rule.targetType) {
                com.mobisec.omniip.db.TargetType.IP_ADDRESS -> ipToLong(targetStr)
                com.mobisec.omniip.db.TargetType.APPLICATION -> targetStr.toLongOrNull() ?: 0L
                else -> HashUtils.murmurHash3(targetStr)
            }
            
            // Mask to 46 bits
            baseKey = baseKey and 0x3FFFFFFFFFFFL

            val directionBits = when (rule.direction) {
                com.mobisec.omniip.model.RuleDirection.BOTH -> 0L
                com.mobisec.omniip.model.RuleDirection.OUTBOUND -> 1L
                com.mobisec.omniip.model.RuleDirection.INBOUND -> 2L
            }

            val portBits = rule.targetPort.toLong() and 0xFFFFL

            keys[i] = (baseKey shl 18) or (directionBits shl 16) or portBits

            fun mapAction(action: com.mobisec.omniip.db.Action): Byte {
                return when (action) {
                    com.mobisec.omniip.db.Action.BLOCK -> 0 // DROP / TARPIT (Tarpit applied natively on RX)
                    com.mobisec.omniip.db.Action.IGNORE -> 1 // ALLOW
                    com.mobisec.omniip.db.Action.FLAG -> 2 // FLAG
                }
            }

            txActions[i] = mapAction(rule.txAction)
            rxActions[i] = mapAction(rule.rxAction)
        }
        
        updateRulesBulk(keys, txActions, rxActions)
    }

    external fun clearNativeRules()
    external fun syncThreatBloomFilter(bitArray: LongArray, hashCount: Int)
    external fun syncDnsBlocklist(domainHashes: LongArray)
}
