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
}
