package com.mobisec.omniip.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoRuleDao {
    @Query("SELECT * FROM geo_rules")
    fun getAllRules(): Flow<List<GeoRule>>

    @Query("SELECT * FROM geo_rules")
    fun getAllRulesSync(): List<GeoRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: GeoRule)

    @Delete
    suspend fun deleteRule(rule: GeoRule)
}
