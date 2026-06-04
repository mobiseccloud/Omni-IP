package com.mobisec.omniip.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FirewallRuleDao {
    @Query("SELECT * FROM firewall_rules")
    fun getAllRules(): Flow<List<FirewallRule>>

    @Query("SELECT * FROM firewall_rules")
    suspend fun getAllRulesSync(): List<FirewallRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FirewallRule)

    @Update
    suspend fun updateRule(rule: FirewallRule)

    @Delete
    suspend fun deleteRule(rule: FirewallRule)
}
