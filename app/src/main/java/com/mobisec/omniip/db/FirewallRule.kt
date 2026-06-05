package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobisec.omniip.model.NetworkInterfaceRule
import com.mobisec.omniip.model.RuleDirection

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
    val timestamp: Long = System.currentTimeMillis(),
    val blockWifi: Boolean = true,
    val blockMobile: Boolean = true,
    val direction: RuleDirection = RuleDirection.BOTH,
    val interfaceRule: NetworkInterfaceRule = NetworkInterfaceRule.ALL
)
