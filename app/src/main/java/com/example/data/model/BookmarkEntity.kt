package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: String,
    val page: Int,
    val label: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSavedPage: Boolean = false
)
