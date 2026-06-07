package com.mobisec.omniip.model

import java.io.Serializable

data class DiscoveredDevice(
    val ipAddress: String,
    val macAddress: String,
    val vendor: String,
    val hostname: String = "Unknown"
) : Serializable
