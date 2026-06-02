package com.mobisec.omniip.model

import android.graphics.drawable.Drawable

data class ConnectionTelemetry(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable?,
    val protocol: String,
    val destPort: Int,
    val destIp: String,
    val resolvedHostname: String?,
    val country: String? = null,
    val countryCode: String? = null,
    val city: String? = null,
    val asn: String? = null,
    val direction: String? = null
)

object ConnectionDirection {
    const val INBOUND = "INBOUND"
    const val OUTBOUND = "OUTBOUND"
}
