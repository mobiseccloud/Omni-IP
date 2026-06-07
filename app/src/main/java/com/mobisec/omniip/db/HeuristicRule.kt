package com.mobisec.omniip.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "heuristic_rules",
    foreignKeys = [ForeignKey(
        entity = IntegrationEndpoint::class,
        parentColumns = ["id"],
        childColumns = ["endpointId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["targetValue", "endpointId"], unique = true)]
)
data class HeuristicRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val endpointId: Int,
    val targetValue: String
)
