package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qa_items")
data class QaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "عام",
    val mode: String = "balanced",
    val isFavorite: Boolean = false
)
