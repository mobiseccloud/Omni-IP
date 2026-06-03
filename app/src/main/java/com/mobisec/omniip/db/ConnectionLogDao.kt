package com.mobisec.omniip.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ConnectionLog)

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ConnectionLog>>

    @Query("SELECT * FROM connection_logs WHERE action IN (:actions) ORDER BY timestamp DESC")
    fun getLogsByActions(actions: List<String>): Flow<List<ConnectionLog>>
}
