package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.dao.BookDao
import com.example.data.dao.BookmarkDao
import com.example.data.dao.PreferenceDao
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
import com.example.data.model.PreferenceEntity
import com.example.util.BookRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val preferenceDao: PreferenceDao
) {
    private val TAG = "BookRepository"

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    // Preferences Keys
    companion object {
        const val KEY_SELECTED_FOLDER_URI = "selected_folder_uri"
        const val KEY_DARK_MODE = "dark_mode" // "light", "dark", "amoled", "dynamic"
        const val KEY_GRID_VIEW = "grid_view" // "true", "false"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on" // "true", "false"
        const val KEY_LAST_READ_BOOK_ID = "last_read_book_id"
    }

    // Preferences Helpers
    suspend fun getPreference(key: String, defaultValue: String): String {
        return preferenceDao.getPreference(key)?.value ?: defaultValue
    }

    fun getPreferenceFlow(key: String, defaultValue: String): Flow<PreferenceEntity?> {
        return preferenceDao.getPreferenceFlow(key)
    }

    suspend fun setPreference(key: String, value: String) {
        preferenceDao.setPreference(PreferenceEntity(key, value))
    }

    suspend fun getBookById(id: String): BookEntity? {
        return bookDao.getBookById(id)
    }

    fun getBookByIdFlow(id: String): Flow<BookEntity?> {
        return bookDao.getBookByIdFlow(id)
    }

    suspend fun insertBook(book: BookEntity) {
        bookDao.insertBook(book)
    }

    suspend fun updateBook(book: BookEntity) {
        bookDao.updateBook(book)
    }

    suspend fun updateProgress(id: String, lastPage: Int, timeDelta: Long) {
        bookDao.updateProgress(id, lastPage, System.currentTimeMillis(), timeDelta)
    }

    suspend fun updateDetailedProgress(
        id: String,
        lastPage: Int,
        timeDelta: Long,
        scrollOffset: Int,
        zoomLevel: Float,
        readingMode: String,
        brightness: Float,
        orientation: String
    ) {
        bookDao.updateDetailedProgress(
            id = id,
            lastPage = lastPage,
            lastOpened = System.currentTimeMillis(),
            timeDelta = timeDelta,
            scrollOffset = scrollOffset,
            zoomLevel = zoomLevel,
            readingMode = readingMode,
            brightness = brightness,
            orientation = orientation
        )
    }

    suspend fun incrementReadingSessions(id: String) {
        bookDao.incrementReadingSessions(id, System.currentTimeMillis())
    }

    suspend fun resetBookHistory(id: String) {
        bookDao.resetBookHistory(id)
    }

    suspend fun resetBooksHistory(ids: List<String>) {
        bookDao.resetBooksHistory(ids)
    }

    suspend fun resetAllBooksHistory() {
        bookDao.resetAllBooksHistory()
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) {
        bookDao.setFavorite(id, isFavorite)
    }

    suspend fun updateCollection(id: String, collection: String?) {
        bookDao.updateCollection(id, collection)
    }

    suspend fun deleteBookFromLibrary(id: String) {
        // Only removes the book from the database. Leaves the physical file alone.
        bookDao.deleteBookById(id)
    }

    // Bookmarks helpers
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForBook(bookId)
    }

    suspend fun addBookmark(bookId: String, page: Int, label: String, isSavedPage: Boolean = false) {
        bookmarkDao.insertBookmark(BookmarkEntity(bookId = bookId, page = page, label = label, isSavedPage = isSavedPage))
    }

    suspend fun updateBookmarkLabel(id: Int, newLabel: String) {
        val existing = bookmarkDao.getBookmarkById(id) ?: return
        bookmarkDao.insertBookmark(existing.copy(label = newLabel))
    }

    suspend fun deleteBookmark(id: Int) {
        bookmarkDao.deleteBookmark(id)
    }

    suspend fun deleteBookmarkAtPage(bookId: String, page: Int) {
        bookmarkDao.deleteBookmarkAtPage(bookId, page)
    }

    /**
     * Scans the selected folder recursively, detecting added and deleted files.
     */
    suspend fun scanSelectedFolder() = withContext(Dispatchers.IO) {
        val folderUriStr = getPreference(KEY_SELECTED_FOLDER_URI, "")
        scanFolder(folderUriStr)
    }

    /**
     * Scans any specific folder recursively, detecting added and deleted files within it.
     */
    suspend fun scanFolder(folderUriStr: String) = withContext(Dispatchers.IO) {
        if (folderUriStr.isEmpty()) {
            Log.d(TAG, "No folder selected. Skipping scan.")
            return@withContext
        }

        _isScanning.value = true
        _scanProgress.value = "Initializing scan..."

        try {
            val rootUri = Uri.parse(folderUriStr)
            val rootDir = DocumentFile.fromTreeUri(context, rootUri)
            if (rootDir == null || !rootDir.exists()) {
                Log.e(TAG, "Selected folder is not accessible or doesn't exist.")
                _scanProgress.value = "Folder is inaccessible. Please re-select."
                _isScanning.value = false
                return@withContext
            }

            // Retrieve current database entries to check for deletions
            val existingBooks = allBooks.firstOrNull() ?: emptyList()
            // Only consider existing books that belong to this folder subtree to avoid deleting books from other folders
            val folderExistingBooks = existingBooks.filter { it.id.startsWith(folderUriStr) }
            val existingIds = folderExistingBooks.map { it.id }.toSet()
            val scannedUris = mutableSetOf<String>()

            // Traverse and index files
            scanDirectory(rootDir, scannedUris, existingIds)

            // Remove books that were deleted from disk (not present in scannedUris)
            for (book in folderExistingBooks) {
                if (book.id !in scannedUris) {
                    // Check if file is really missing on SAF
                    try {
                        val fileDoc = DocumentFile.fromSingleUri(context, Uri.parse(book.id))
                        if (fileDoc == null || !fileDoc.exists()) {
                            Log.d(TAG, "Book no longer exists on disk, removing: ${book.title}")
                            bookDao.deleteBookById(book.id)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not verify existence of ${book.title}, removing from library database", e)
                        bookDao.deleteBookById(book.id)
                    }
                }
            }

            _scanProgress.value = "Scan completed successfully!"
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder", e)
            _scanProgress.value = "Scan error: ${e.localizedMessage}"
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun scanDirectory(
        directory: DocumentFile,
        scannedUris: MutableSet<String>,
        existingIds: Set<String>
    ) {
        val files = directory.listFiles()
        for (i in files.indices) {
            val file = files[i]
            if (file.isDirectory) {
                scanDirectory(file, scannedUris, existingIds)
            } else {
                val fileName = file.name ?: continue
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext == "pdf" || ext == "cbz") {
                    val uriString = file.uri.toString()
                    scannedUris.add(uriString)

                    if (uriString !in existingIds) {
                        _scanProgress.value = "Indexing: $fileName"
                        indexNewBook(file, uriString, ext)
                    }
                }
            }
        }
    }

    private suspend fun indexNewBook(file: DocumentFile, uriString: String, format: String) {
        try {
            val title = file.name?.substringBeforeLast('.') ?: "Unknown Book"
            val fileSize = file.length()
            var pageCount = 0

            // Determine page count based on format
            if (format == "pdf") {
                try {
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        pageCount = renderer.pageCount
                        renderer.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get page count for PDF: $title", e)
                }
            } else if (format == "cbz") {
                try {
                    pageCount = BookRenderer.getCbzImageEntries(context, file.uri).size
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get page count for CBZ: $title", e)
                }
            }

            // Generate cache cover thumbnail
            val coverPath = BookRenderer.generateCoverThumbnail(context, uriString, uriString, format)

            val book = BookEntity(
                id = uriString,
                title = title,
                fileSize = fileSize,
                pageCount = pageCount,
                format = format,
                filePath = uriString,
                collection = null,
                dateAdded = System.currentTimeMillis()
            )
            bookDao.insertBook(book)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to index book ${file.name}", e)
        }
    }

    /**
     * Deletes all data to start fresh.
     */
    suspend fun clearDatabase() {
        bookDao.deleteAllBooks()
        preferenceDao.deletePreference(KEY_SELECTED_FOLDER_URI)
    }
}
