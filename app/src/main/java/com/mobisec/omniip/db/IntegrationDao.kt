package com.mobisec.omniip.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface IntegrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEndpoint(endpoint: IntegrationEndpoint): Long

    @Query("SELECT * FROM integration_endpoints")
    suspend fun getAllEndpoints(): List<IntegrationEndpoint>

    @Query("SELECT * FROM integration_endpoints WHERE id = :id")
    suspend fun getEndpointById(id: Int): IntegrationEndpoint?

    @Query("UPDATE integration_endpoints SET sequenceId = :sequenceId WHERE id = :id")
    suspend fun updateSequenceId(id: Int, sequenceId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeuristicRules(rules: List<HeuristicRule>)

    @Query("DELETE FROM heuristic_rules WHERE endpointId = :endpointId AND targetValue IN (:targets)")
    suspend fun deleteHeuristicRules(endpointId: Int, targets: List<String>)

    @Query("SELECT * FROM heuristic_rules WHERE targetValue = :targetValue")
    suspend fun getRulesForTarget(targetValue: String): List<HeuristicRule>

    @Query("SELECT * FROM heuristic_rules")
    suspend fun getAllHeuristicRules(): List<HeuristicRule>

    @Query("DELETE FROM heuristic_rules WHERE endpointId = :endpointId")
    suspend fun deleteHeuristicRulesByEndpointId(endpointId: Int)

    @Transaction
    suspend fun processDeltaSync(endpointId: Int, newSequenceId: Long, adds: List<String>, removes: List<String>) {
        if (adds.isNotEmpty()) {
            val rules = adds.map { HeuristicRule(endpointId = endpointId, targetValue = it) }
            insertHeuristicRules(rules)
        }
        if (removes.isNotEmpty()) {
            deleteHeuristicRules(endpointId, removes)
        }
        updateSequenceId(endpointId, newSequenceId)
    }
}
