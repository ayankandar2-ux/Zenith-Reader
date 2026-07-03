package com.example.data.dao

import androidx.room.*
import com.example.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: String): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET lastPage = :lastPage, lastOpened = :lastOpened, readingTime = readingTime + :timeDelta WHERE id = :id")
    suspend fun updateProgress(id: String, lastPage: Int, lastOpened: Long, timeDelta: Long)

    @Query("UPDATE books SET lastPage = :lastPage, lastOpened = :lastOpened, readingTime = readingTime + :timeDelta, scrollOffset = :scrollOffset, zoomLevel = :zoomLevel, readingMode = :readingMode, brightness = :brightness, orientation = :orientation WHERE id = :id")
    suspend fun updateDetailedProgress(id: String, lastPage: Int, lastOpened: Long, timeDelta: Long, scrollOffset: Int, zoomLevel: Float, readingMode: String, brightness: Float, orientation: String)

    @Query("UPDATE books SET readingSessions = readingSessions + 1, lastOpened = :lastOpened WHERE id = :id")
    suspend fun incrementReadingSessions(id: String, lastOpened: Long)

    @Query("UPDATE books SET lastPage = 0, lastOpened = 0, readingTime = 0, scrollOffset = 0, zoomLevel = 1.0, readingSessions = 0 WHERE id = :id")
    suspend fun resetBookHistory(id: String)

    @Query("UPDATE books SET lastPage = 0, lastOpened = 0, readingTime = 0, scrollOffset = 0, zoomLevel = 1.0, readingSessions = 0 WHERE id IN (:ids)")
    suspend fun resetBooksHistory(ids: List<String>)

    @Query("UPDATE books SET lastPage = 0, lastOpened = 0, readingTime = 0, scrollOffset = 0, zoomLevel = 1.0, readingSessions = 0")
    suspend fun resetAllBooksHistory()

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE books SET collection = :collection WHERE id = :id")
    suspend fun updateCollection(id: String, collection: String?)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()
}
