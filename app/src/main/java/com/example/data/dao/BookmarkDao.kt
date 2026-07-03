package com.example.data.dao

import androidx.room.*
import com.example.data.model.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY page ASC")
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getBookmarkById(id: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Int)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND page = :page")
    suspend fun deleteBookmarkAtPage(bookId: String, page: Int)
}
