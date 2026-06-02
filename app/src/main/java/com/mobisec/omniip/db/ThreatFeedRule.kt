package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_feed_rules")
data class ThreatFeedRule(
    @PrimaryKey val targetValue: String,
    val feedType: String // e.g., "ad_tracker", "malware"
)
