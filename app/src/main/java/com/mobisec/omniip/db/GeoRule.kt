package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geo_rules")
data class GeoRule(
    @PrimaryKey val countryCode: String, // ISO alpha-2 string (e.g., "CN", "RU")
    val action: String,                 // "BLOCK" or "FLAG"
    val timestamp: Long
)
