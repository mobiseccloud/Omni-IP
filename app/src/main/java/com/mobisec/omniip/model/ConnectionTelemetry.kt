package com.mobisec.omniip.model

import android.graphics.drawable.Drawable

/**
 * Strongly-typed direction enum for bi-directional packet visibility.
 * Used by honeypot detection logic and the accordion telemetry UI.
 * INBOUND = traffic arriving at the device (honeypot trigger source).
 * OUTBOUND = traffic originating from the device (C2 detection).
 */
enum class ConnectionDirection {
    INBOUND,
    OUTBOUND;

    companion object {
        // Legacy string constants kept for interop with any existing string comparisons
        const val INBOUND_STR = "INBOUND"
        const val OUTBOUND_STR = "OUTBOUND"

        fun fromString(s: String?): ConnectionDirection? = when (s) {
            INBOUND_STR -> INBOUND
            OUTBOUND_STR -> OUTBOUND
            else -> null
        }
    }
}

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
    // direction is now a String? for backward compat with OmniVpnService which produces string constants
    val direction: String? = null,
    val uid: Int = -1,
    val isBlocked: Boolean = false,
    val isFlagged: Boolean = false,
    val isIgnored: Boolean = false,
    val txBytes: Int = 0,
    val rxBytes: Int = 0
)
