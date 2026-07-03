package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String, // filePath or unique content URI
    val title: String,
    val fileSize: Long,
    val pageCount: Int,
    val lastPage: Int = 0,
    val lastOpened: Long = 0,
    val readingTime: Long = 0, // in milliseconds
    val isFavorite: Boolean = false,
    val format: String, // "pdf" or "cbz"
    val filePath: String,
    val collection: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val scrollOffset: Int = 0,
    val zoomLevel: Float = 1.0f,
    val readingMode: String = "vertical",
    val brightness: Float = -1.0f,
    val orientation: String = "sensor",
    val readingSessions: Int = 0
)
