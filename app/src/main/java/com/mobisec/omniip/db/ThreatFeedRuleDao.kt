package com.mobisec.omniip.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThreatFeedRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ThreatFeedRule>)

    @Query("DELETE FROM threat_feed_rules WHERE feedType = :feedType")
    suspend fun deleteByFeedType(feedType: String)

    @Query("SELECT targetValue FROM threat_feed_rules")
    suspend fun getAllTargets(): List<String>

    @Query("SELECT targetValue FROM threat_feed_rules WHERE feedType = :feedType")
    suspend fun getTargetsByFeedType(feedType: String): List<String>
}
