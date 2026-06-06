package com.mobisec.omniip.model

import android.graphics.drawable.Drawable

data class ConnectionDetails(
    val signature: String,
    val protocol: String,
    val destIp: String,
    val destPort: Int,
    val isBlocked: Boolean,
    val direction: String?,
    var bytesTx: Long = 0,
    var bytesRx: Long = 0,
    var packetCount: Long = 0,
    var lastActive: Long = System.currentTimeMillis(),
    val countryCode: String? = null,
    val city: String? = null
)

data class AppConnectionSummary(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val appIcon: Drawable?,
    val totalTx: Long,
    val totalRx: Long,
    val totalBlocked: Long,
    val activeConnections: List<ConnectionDetails>
)
