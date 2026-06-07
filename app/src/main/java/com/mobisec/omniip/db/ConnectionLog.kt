package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_logs")
data class ConnectionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val destIp: String,
    val destPort: Int,
    val asn: String?,
    val countryCode: String?,
    val city: String?,
    val appName: String,
    val action: String, // ALLOW, BLOCK, FLAG
    val timestamp: Long = System.currentTimeMillis(),
    val protocol: String? = null,
    val sourceIp: String? = null,
    val sourcePort: Int? = null,
    val domainName: String? = null,
    val country: String? = null,
    val direction: String? = null
)
