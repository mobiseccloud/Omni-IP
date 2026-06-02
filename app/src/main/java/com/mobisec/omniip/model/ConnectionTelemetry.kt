package com.mobisec.omniip.model

import android.graphics.drawable.Drawable

data class ConnectionTelemetry(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable?,
    val protocol: String,
    val destPort: Int,
    val destIp: String,
    val resolvedHostname: String?
)
