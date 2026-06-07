package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geo_rules")
data class GeoRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val countryCode: String, // ISO alpha-2 string (e.g., "CN", "RU")
    val city: String? = null,
    val action: String,      // "BLOCK" or "FLAG"
    val timestamp: Long
)
