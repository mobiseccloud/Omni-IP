package com.mobisec.omniip.model

data class DiscoveredDevice(
    val ipAddress: String,
    val macAddress: String,
    val vendor: String
)
