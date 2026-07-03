package com.example.di

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.BookRepository

object AppContainer {
    @Volatile
    private var database: AppDatabase? = null
    @Volatile
    private var repository: BookRepository? = null

    fun getRepository(context: Context): BookRepository {
        return repository ?: synchronized(this) {
            val db = database ?: AppDatabase.getDatabase(context).also { database = it }
            val repo = BookRepository(
                context = context.applicationContext,
                bookDao = db.bookDao(),
                bookmarkDao = db.bookmarkDao(),
                preferenceDao = db.preferenceDao()
            )
            repository = repo
            repo
        }
    }
}
