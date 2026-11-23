package com.digitalsignage.tv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val priority: Int = 1,
    val validFrom: Long = 0L,
    val validTo: Long = Long.MAX_VALUE,
    val activeDaysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val isActive: Boolean = true
)
