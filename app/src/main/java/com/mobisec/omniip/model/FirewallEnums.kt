package com.mobisec.omniip.model

enum class RuleDirection {
    INBOUND,   // Rx: Drop incoming server responses at virtual interface
    OUTBOUND,  // Tx: Drop traffic originating from the local application
    BOTH       // Enforce restrictions bi-directionally
}

enum class NetworkInterfaceRule {
    ALL,
    WIFI_ONLY,
    CELLULAR_ONLY,
    BLOCKED_ALL
}
