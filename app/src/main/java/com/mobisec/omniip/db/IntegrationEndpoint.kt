package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "integration_endpoints")
data class IntegrationEndpoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val baseUrl: String,
    val apiKey: String?, 
    val endpointType: String, // "DOMAIN_LIST", "IP_LIST", "APK_PACKAGE_LIST"
    val actionPolicy: String, // "BLOCK", "FLAG", "IGNORE"
    val priorityLevel: Int,   // Priority across multiple managed endpoints
    val sequenceId: Long      // Tracks the latest committed Delta-Sync iteration
)
