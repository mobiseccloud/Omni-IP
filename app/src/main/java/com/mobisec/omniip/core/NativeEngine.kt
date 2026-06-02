package com.mobisec.omniip.core

object NativeEngine {
    init {
        System.loadLibrary("omniip_bridge")
    }

    external fun executeNmapScan(target: String): String
    external fun executeRawPing(target: String): String
}
