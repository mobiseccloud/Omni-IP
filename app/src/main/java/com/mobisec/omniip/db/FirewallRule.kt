package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TargetType {
    APPLICATION, IP_ADDRESS, DOMAIN
}

enum class Action {
    BLOCK, FLAG, IGNORE
}

@Entity(tableName = "firewall_rules")
data class FirewallRule(
    @PrimaryKey(autoGenerate = true) val ruleId: Int = 0,
    val targetType: TargetType,
    val targetValue: String,
    val action: Action,
    val timestamp: Long = System.currentTimeMillis()
)
