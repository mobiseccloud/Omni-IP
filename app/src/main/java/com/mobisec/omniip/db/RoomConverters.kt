package com.mobisec.omniip.db

import androidx.room.TypeConverter
import com.mobisec.omniip.model.NetworkInterfaceRule
import com.mobisec.omniip.model.RuleDirection

class RoomConverters {

    @TypeConverter
    fun fromRuleDirection(value: RuleDirection): String {
        return value.name
    }

    @TypeConverter
    fun toRuleDirection(value: String): RuleDirection {
        return try {
            RuleDirection.valueOf(value)
        } catch (e: IllegalArgumentException) {
            RuleDirection.BOTH
        }
    }

    @TypeConverter
    fun fromNetworkInterfaceRule(value: NetworkInterfaceRule): String {
        return value.name
    }

    @TypeConverter
    fun toNetworkInterfaceRule(value: String): NetworkInterfaceRule {
        return try {
            NetworkInterfaceRule.valueOf(value)
        } catch (e: IllegalArgumentException) {
            NetworkInterfaceRule.ALL
        }
    }
}
