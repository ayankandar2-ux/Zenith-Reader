package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.di.AppContainer
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "BookViewModel"
    private val repository = AppContainer.getRepository(application)

    // Scanning States
    val isScanning = repository.isScanning
    val scanProgress = repository.scanProgress

    // Preferences & Layout states
    private val _selectedFolderUri = MutableStateFlow("")
    val selectedFolderUri = _selectedFolderUri.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView = _isGridView.asStateFlow()

    private val _darkModeSetting = MutableStateFlow("dark") // "light", "dark", "amoled", "dynamic"
    val darkModeSetting = _darkModeSetting.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn = _keepScreenOn.asStateFlow()

    // --- PERSONALIZATION SETTINGS ---
    private val _accentColorSetting = MutableStateFlow("Teal") // Blue, Red, Green, Purple, Orange, Yellow, Pink, Cyan, Teal, Indigo
    val accentColorSetting = _accentColorSetting.asStateFlow()

    private val _materialYouSetting = MutableStateFlow(false)
    val materialYouSetting = _materialYouSetting.asStateFlow()

    private val _cornerStyleSetting = MutableStateFlow("rounded") // rounded, medium, square
    val cornerStyleSetting = _cornerStyleSetting.asStateFlow()

    private val _uiFontSizeSetting = MutableStateFlow("default") // small, default, large, extra_large
    val uiFontSizeSetting = _uiFontSizeSetting.asStateFlow()

    private val _readerFontSizeSetting = MutableStateFlow("default") // small, default, large, extra_large
    val readerFontSizeSetting = _readerFontSizeSetting.asStateFlow()

    private val _iconsStyleSetting = MutableStateFlow("filled") // filled, outlined, rounded
    val iconsStyleSetting = _iconsStyleSetting.asStateFlow()

    private val _layoutSpacingSetting = MutableStateFlow("comfortable") // compact, comfortable, large
    val layoutSpacingSetting = _layoutSpacingSetting.asStateFlow()

    private val _navigationStyleSetting = MutableStateFlow("bottom_nav") // bottom_nav, drawer, rail
    val navigationStyleSetting = _navigationStyleSetting.asStateFlow()

    private val _animationsLevelSetting = MutableStateFlow("full") // full, reduced, none
    val animationsLevelSetting = _animationsLevelSetting.asStateFlow()

    private val _appLanguageSetting = MutableStateFlow("en") // en, es, fr, ja, zh
    val appLanguageSetting = _appLanguageSetting.asStateFlow()

    private val _volumeKeysPageTurn = MutableStateFlow(false)
    val volumeKeysPageTurn = _volumeKeysPageTurn.asStateFlow()

    // --- FOLDERS CONFIGS ---
    private val _foldersList = MutableStateFlow<List<FolderConfig>>(emptyList())
    val foldersList = _foldersList.asStateFlow()

    private val _activeFolderUri = MutableStateFlow("") // "" or "all" means All Folders
    val activeFolderUri = _activeFolderUri.asStateFlow()

    private val _recentFolders = MutableStateFlow<List<String>>(emptyList())
    val recentFolders = _recentFolders.asStateFlow()

    // Library Filter and Sort states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    enum class SortType { TITLE, DATE_ADDED, LAST_OPENED, FILE_SIZE, PROGRESS }
    private val _sortBy = MutableStateFlow(SortType.TITLE)
    val sortBy = _sortBy.asStateFlow()

    enum class FilterType { ALL, FAVORITES, CONTINUE_READING, RECENTLY_ADDED, COMPLETED }
    private val _filterBy = MutableStateFlow(FilterType.ALL)
    val filterBy = _filterBy.asStateFlow()

    // Multi-select state
    private val _selectedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBookIds = _selectedBookIds.asStateFlow()

    private val _isMultiSelectActive = MutableStateFlow(false)
    val isMultiSelectActive = _isMultiSelectActive.asStateFlow()

    // Books stream directly from Database, coupled with UI filters, sorts, and active folder
    val booksState: StateFlow<List<BookEntity>> = combine(
        repository.allBooks,
        _searchQuery,
        _sortBy,
        _filterBy,
        _activeFolderUri
    ) { allBooks, query, sort, filter, activeFolder ->
        var list = allBooks

        // Apply Active Folder Filter
        if (activeFolder.isNotEmpty() && activeFolder != "all") {
            list = list.filter { it.id.startsWith(activeFolder) }
        }

        // Apply Search
        if (query.isNotEmpty()) {
            list = list.filter { it.title.contains(query, ignoreCase = true) }
        }

        // Apply Filters
        list = when (filter) {
            FilterType.ALL -> list
            FilterType.FAVORITES -> list.filter { it.isFavorite }
            FilterType.CONTINUE_READING -> list.filter { it.lastPage > 0 && it.lastPage < it.pageCount - 1 }
            FilterType.RECENTLY_ADDED -> list.sortedByDescending { it.dateAdded }.take(10)
            FilterType.COMPLETED -> list.filter { it.lastPage >= it.pageCount - 1 && it.pageCount > 0 }
        }

        // Apply Sorts
        if (filter != FilterType.RECENTLY_ADDED) {
            list = when (sort) {
                SortType.TITLE -> list.sortedBy { it.title.lowercase() }
                SortType.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
                SortType.LAST_OPENED -> list.sortedByDescending { it.lastOpened }
                SortType.FILE_SIZE -> list.sortedByDescending { it.fileSize }
                SortType.PROGRESS -> list.sortedByDescending {
                    if (it.pageCount > 0) it.lastPage.toFloat() / it.pageCount else 0f
                }
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Reader States
    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook = _currentBook.asStateFlow()

    private val _currentBookId = MutableStateFlow<String?>(null)
    val currentBookBookmarks: StateFlow<List<BookmarkEntity>> = _currentBookId
        .filterNotNull()
        .flatMapLatest { repository.getBookmarksForBook(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom reader settings
    private val _readerOrientation = MutableStateFlow("sensor") // "sensor", "portrait", "landscape"
    val readerOrientation = _readerOrientation.asStateFlow()

    private val _readerMode = MutableStateFlow("vertical") // "vertical", "horizontal", "single", "webtoon"
    val readerMode = _readerMode.asStateFlow()

    private val _readerBrightness = MutableStateFlow(-1f) // -1f means system brightness
    val readerBrightness = _readerBrightness.asStateFlow()

    // Tracking time active
    private var readingStartTime: Long = 0

    init {
        // Load initial preferences
        viewModelScope.launch {
            _selectedFolderUri.value = repository.getPreference(BookRepository.KEY_SELECTED_FOLDER_URI, "")
            _isGridView.value = repository.getPreference(BookRepository.KEY_GRID_VIEW, "true").toBoolean()
            _darkModeSetting.value = repository.getPreference(BookRepository.KEY_DARK_MODE, "dark")
            _keepScreenOn.value = repository.getPreference(BookRepository.KEY_KEEP_SCREEN_ON, "false").toBoolean()

            // Load Personalization settings
            _accentColorSetting.value = repository.getPreference("accent_color", "Teal")
            _materialYouSetting.value = repository.getPreference("material_you", "false").toBoolean()
            _cornerStyleSetting.value = repository.getPreference("corner_style", "rounded")
            _uiFontSizeSetting.value = repository.getPreference("ui_font_size", "default")
            _readerFontSizeSetting.value = repository.getPreference("reader_font_size", "default")
            _iconsStyleSetting.value = repository.getPreference("icons_style", "filled")
            _layoutSpacingSetting.value = repository.getPreference("layout_spacing", "comfortable")
            _navigationStyleSetting.value = repository.getPreference("navigation_style", "bottom_nav")
            _animationsLevelSetting.value = repository.getPreference("animations_level", "full")
            _appLanguageSetting.value = repository.getPreference("app_language", "en")
            _volumeKeysPageTurn.value = repository.getPreference("volume_keys_page_turn", "false").toBoolean()

            // Load folders list
            loadFolders()

            // If a folder has already been selected, perform automatic scan in the background
            if (_selectedFolderUri.value.isNotEmpty()) {
                runScan()
            }
        }
    }

    fun setFolderUri(uri: String) {
        viewModelScope.launch {
            repository.setPreference(BookRepository.KEY_SELECTED_FOLDER_URI, uri)
            _selectedFolderUri.value = uri
            com.example.util.SampleDataGenerator.generateDemoFilesIfEmpty(getApplication(), uri)
            
            // Add to multiple folders list automatically
            val alias = getPathDisplayName(uri)
            addFolder(uri, alias)
        }
    }

    fun runScan() {
        viewModelScope.launch {
            if (_selectedFolderUri.value.isNotEmpty()) {
                com.example.util.SampleDataGenerator.generateDemoFilesIfEmpty(getApplication(), _selectedFolderUri.value)
            }
            repository.scanSelectedFolder()
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.setFavorite(book.id, !book.isFavorite)
        }
    }

    fun updateBookProgress(bookId: String, lastPage: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val timeDelta = if (readingStartTime > 0) now - readingStartTime else 0L
            readingStartTime = now // reset anchor

            repository.updateProgress(bookId, lastPage, timeDelta)
            // Refresh current book object in memory
            _currentBook.value?.let { current ->
                if (current.id == bookId) {
                    _currentBook.value = current.copy(
                        lastPage = lastPage,
                        lastOpened = now,
                        readingTime = current.readingTime + timeDelta
                    )
                }
            }
        }
    }

    fun updateBookProgressDetailed(
        bookId: String,
        lastPage: Int,
        scrollOffset: Int,
        zoomLevel: Float,
        readingMode: String,
        brightness: Float,
        orientation: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val timeDelta = if (readingStartTime > 0) now - readingStartTime else 0L
            readingStartTime = now // reset anchor

            repository.updateDetailedProgress(
                id = bookId,
                lastPage = lastPage,
                timeDelta = timeDelta,
                scrollOffset = scrollOffset,
                zoomLevel = zoomLevel,
                readingMode = readingMode,
                brightness = brightness,
                orientation = orientation
            )

            // Refresh current book object in memory
            _currentBook.value?.let { current ->
                if (current.id == bookId) {
                    _currentBook.value = current.copy(
                        lastPage = lastPage,
                        lastOpened = now,
                        readingTime = current.readingTime + timeDelta,
                        scrollOffset = scrollOffset,
                        zoomLevel = zoomLevel,
                        readingMode = readingMode,
                        brightness = brightness,
                        orientation = orientation
                    )
                }
            }
        }
    }

    // Set open book & reading session anchor
    fun openBook(book: BookEntity) {
        _currentBook.value = book
        _currentBookId.value = book.id
        readingStartTime = System.currentTimeMillis()
        
        // Load settings saved specifically on this book
        _readerMode.value = book.readingMode
        _readerBrightness.value = book.brightness
        _readerOrientation.value = book.orientation
        
        viewModelScope.launch {
            repository.setPreference(BookRepository.KEY_LAST_READ_BOOK_ID, book.id)
            repository.incrementReadingSessions(book.id)
            
            // Refresh readingSessions locally
            _currentBook.value = book.copy(
                readingSessions = book.readingSessions + 1,
                lastOpened = System.currentTimeMillis()
            )
        }
    }

    fun closeBook() {
        _currentBook.value?.let { book ->
            updateBookProgressDetailed(
                bookId = book.id,
                lastPage = book.lastPage,
                scrollOffset = book.scrollOffset,
                zoomLevel = book.zoomLevel,
                readingMode = book.readingMode,
                brightness = book.brightness,
                orientation = book.orientation
            )
        }
        _currentBook.value = null
        _currentBookId.value = null
        readingStartTime = 0
    }

    // Bookmarks management
    fun addBookmark(page: Int, label: String) {
        val bookId = _currentBookId.value ?: return
        viewModelScope.launch {
            repository.addBookmark(bookId, page, label)
        }
    }

    fun deleteBookmark(bookmarkId: Int) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
        }
    }

    fun toggleBookmarkAtPage(page: Int) {
        val bookId = _currentBookId.value ?: return
        val bookmarks = currentBookBookmarks.value
        val existing = bookmarks.find { it.page == page }
        viewModelScope.launch {
            if (existing != null) {
                repository.deleteBookmark(existing.id)
            } else {
                repository.addBookmark(bookId, page, "Page ${page + 1}")
            }
        }
    }

    // General Preferences setters
    fun setGridView(isGrid: Boolean) {
        viewModelScope.launch {
            repository.setPreference(BookRepository.KEY_GRID_VIEW, isGrid.toString())
            _isGridView.value = isGrid
        }
    }

    fun setDarkMode(setting: String) {
        viewModelScope.launch {
            repository.setPreference(BookRepository.KEY_DARK_MODE, setting)
            _darkModeSetting.value = setting
        }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch {
            repository.setPreference(BookRepository.KEY_KEEP_SCREEN_ON, on.toString())
            _keepScreenOn.value = on
        }
    }

    fun setSortBy(sort: SortType) {
        _sortBy.value = sort
    }

    fun setFilterBy(filter: FilterType) {
        _filterBy.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Reader local customization setters
    fun setReaderOrientation(orientation: String) {
        _readerOrientation.value = orientation
    }

    fun setReaderMode(mode: String) {
        _readerMode.value = mode
    }

    fun setReaderBrightness(brightness: Float) {
        _readerBrightness.value = brightness
    }

    // Multi-select actions
    fun toggleBookSelection(bookId: String) {
        val current = _selectedBookIds.value.toMutableSet()
        if (current.contains(bookId)) {
            current.remove(bookId)
        } else {
            current.add(bookId)
        }
        _selectedBookIds.value = current
        _isMultiSelectActive.value = current.isNotEmpty()
    }

    fun clearSelection() {
        _selectedBookIds.value = emptySet()
        _isMultiSelectActive.value = false
    }

    fun deleteSelectedFromLibrary() {
        val idsToDelete = _selectedBookIds.value
        viewModelScope.launch {
            idsToDelete.forEach { id ->
                repository.deleteBookFromLibrary(id)
            }
            clearSelection()
        }
    }

    fun deleteSingleBookFromLibrary(bookId: String) {
        viewModelScope.launch {
            repository.deleteBookFromLibrary(bookId)
        }
    }

    // Folder statistics generator
    fun getFolderStatistics(): FolderStats {
        val allBooks = booksState.value
        val totalBooks = allBooks.size
        val pdfCount = allBooks.count { it.format == "pdf" }
        val cbzCount = allBooks.count { it.format == "cbz" }
        val totalBytes = allBooks.sumOf { it.fileSize }
        val totalTimeMinutes = allBooks.sumOf { it.readingTime } / (1000 * 60)

        // Parse human folder name
        val rawUri = _selectedFolderUri.value
        val folderName = try {
            val uri = Uri.parse(rawUri)
            val path = uri.path ?: ""
            if (path.contains(":")) path.substringAfterLast(":") else path.substringAfterLast("/")
        } catch (e: Exception) {
            "Selected Folder"
        }

        return FolderStats(
            folderName = folderName,
            totalBooks = totalBooks,
            pdfCount = pdfCount,
            cbzCount = cbzCount,
            totalBytes = totalBytes,
            totalReadingTimeMinutes = totalTimeMinutes
        )
    }

    fun resetBookHistory(bookId: String) {
        viewModelScope.launch {
            repository.resetBookHistory(bookId)
        }
    }

    fun resetBooksHistory(bookIds: List<String>) {
        viewModelScope.launch {
            repository.resetBooksHistory(bookIds)
        }
    }

    fun resetAllBooksHistory() {
        viewModelScope.launch {
            repository.resetAllBooksHistory()
        }
    }

    fun exportHistoryToJson(): String {
        return try {
            val historyBooks = booksState.value.filter { it.lastOpened > 0 }
            val array = org.json.JSONArray()
            for (book in historyBooks) {
                val obj = org.json.JSONObject()
                obj.put("id", book.id)
                obj.put("title", book.title)
                obj.put("lastPage", book.lastPage)
                obj.put("lastOpened", book.lastOpened)
                obj.put("readingTime", book.readingTime)
                obj.put("scrollOffset", book.scrollOffset)
                obj.put("readingSessions", book.readingSessions)
                array.put(obj)
            }
            array.toString(2)
        } catch (e: Exception) {
            Log.e("BookViewModel", "Failed to export history", e)
            ""
        }
    }

    fun importHistoryFromJson(json: String): Boolean {
        return try {
            val list = org.json.JSONArray(json)
            viewModelScope.launch {
                for (i in 0 until list.length()) {
                    val obj = list.getJSONObject(i)
                    val id = obj.getString("id")
                    val lastPage = obj.getInt("lastPage")
                    val lastOpened = obj.getLong("lastOpened")
                    val readingTime = obj.getLong("readingTime")
                    val scrollOffset = obj.optInt("scrollOffset", 0)
                    val readingSessions = obj.optInt("readingSessions", 1)

                    val existing = repository.getBookById(id)
                    if (existing != null) {
                        repository.updateDetailedProgress(
                            id = id,
                            lastPage = lastPage,
                            timeDelta = readingTime - existing.readingTime,
                            scrollOffset = scrollOffset,
                            zoomLevel = existing.zoomLevel,
                            readingMode = existing.readingMode,
                            brightness = existing.brightness,
                            orientation = existing.orientation
                        )
                        repository.updateBook(existing.copy(
                            lastPage = lastPage,
                            lastOpened = lastOpened,
                            readingTime = readingTime,
                            scrollOffset = scrollOffset,
                            readingSessions = readingSessions
                        ))
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BookViewModel", "Failed to import history", e)
            false
        }
    }

    suspend fun resetAllData() {
        withContext(Dispatchers.IO) {
            repository.clearDatabase()
            _selectedFolderUri.value = ""
            _currentBook.value = null
            _currentBookId.value = null
            clearSelection()
        }
    }

    // --- NEW PERSONALIZATION GETTERS & SETTERS ---
    fun setAccentColor(accent: String) {
        viewModelScope.launch {
            repository.setPreference("accent_color", accent)
            _accentColorSetting.value = accent
        }
    }

    fun setMaterialYou(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference("material_you", enabled.toString())
            _materialYouSetting.value = enabled
        }
    }

    fun setCornerStyle(style: String) {
        viewModelScope.launch {
            repository.setPreference("corner_style", style)
            _cornerStyleSetting.value = style
        }
    }

    fun setUiFontSize(size: String) {
        viewModelScope.launch {
            repository.setPreference("ui_font_size", size)
            _uiFontSizeSetting.value = size
        }
    }

    fun setReaderFontSize(size: String) {
        viewModelScope.launch {
            repository.setPreference("reader_font_size", size)
            _readerFontSizeSetting.value = size
        }
    }

    fun setIconsStyle(style: String) {
        viewModelScope.launch {
            repository.setPreference("icons_style", style)
            _iconsStyleSetting.value = style
        }
    }

    fun setLayoutSpacing(spacing: String) {
        viewModelScope.launch {
            repository.setPreference("layout_spacing", spacing)
            _layoutSpacingSetting.value = spacing
        }
    }

    fun setNavigationStyle(style: String) {
        viewModelScope.launch {
            repository.setPreference("navigation_style", style)
            _navigationStyleSetting.value = style
        }
    }

    fun setAnimationsLevel(level: String) {
        viewModelScope.launch {
            repository.setPreference("animations_level", level)
            _animationsLevelSetting.value = level
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            repository.setPreference("app_language", lang)
            _appLanguageSetting.value = lang
        }
    }

    fun setVolumeKeysPageTurn(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference("volume_keys_page_turn", enabled.toString())
            _volumeKeysPageTurn.value = enabled
        }
    }

    // --- SAVE PAGE & DETAILED BOOKMARKS ---
    fun savePageManually(page: Int) {
        val bookId = _currentBookId.value ?: return
        viewModelScope.launch {
            repository.addBookmark(bookId, page, "Saved Page ${page + 1}", isSavedPage = true)
        }
    }

    fun renameBookmark(id: Int, newLabel: String) {
        viewModelScope.launch {
            repository.updateBookmarkLabel(id, newLabel)
        }
    }

    // --- VOLUME PAGE TURN CHANNEL ---
    private val _pageTurnEvent = MutableSharedFlow<Boolean>()
    val pageTurnEvent = _pageTurnEvent.asSharedFlow()

    fun turnPage(isForward: Boolean) {
        viewModelScope.launch {
            _pageTurnEvent.emit(isForward)
        }
    }

    // --- MULTIPLE FOLDERS MANAGEMENT ---
    fun getPathDisplayName(uriStr: String): String {
        return try {
            val uri = Uri.parse(uriStr)
            val path = uri.path ?: ""
            if (path.contains(":")) path.substringAfterLast(":") else path.substringAfterLast("/")
        } catch (e: Exception) {
            "Storage Folder"
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            val jsonStr = repository.getPreference("folder_configs", "[]")
            val activeUri = repository.getPreference("active_folder_uri", "")
            _activeFolderUri.value = activeUri
            
            val context = getApplication<Application>()
            val persistedUris = try {
                context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
            
            val list = mutableListOf<FolderConfig>()
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uri = obj.getString("uri")
                    val granted = uri.isEmpty() || persistedUris.contains(uri)
                    list.add(FolderConfig(
                        uri = uri,
                        alias = obj.getString("alias"),
                        path = obj.getString("path"),
                        pdfCount = obj.optInt("pdfCount", 0),
                        cbzCount = obj.optInt("cbzCount", 0),
                        totalBooks = obj.optInt("totalBooks", 0),
                        totalBytes = obj.optLong("totalBytes", 0),
                        lastScanTime = obj.optLong("lastScanTime", 0),
                        permissionGranted = granted
                    ))
                }
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to load folder configs", e)
            }
            
            if (list.isEmpty() && _selectedFolderUri.value.isNotEmpty()) {
                val defaultPath = getPathDisplayName(_selectedFolderUri.value)
                val granted = persistedUris.contains(_selectedFolderUri.value)
                val defaultFolder = FolderConfig(
                    uri = _selectedFolderUri.value,
                    alias = "Default Library",
                    path = defaultPath,
                    permissionGranted = granted
                )
                list.add(defaultFolder)
                saveFoldersList(list)
                if (_activeFolderUri.value.isEmpty()) {
                    _activeFolderUri.value = _selectedFolderUri.value
                    repository.setPreference("active_folder_uri", _selectedFolderUri.value)
                }
            }
            
            _foldersList.value = list
            
            // Load recent folders list
            val recentJsonStr = repository.getPreference("recent_folders", "[]")
            val recentList = mutableListOf<String>()
            try {
                val arr = org.json.JSONArray(recentJsonStr)
                for (i in 0 until arr.length()) {
                    recentList.add(arr.getString(i))
                }
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to load recent folders", e)
            }
            _recentFolders.value = recentList
        }
    }

    fun saveFoldersList(list: List<FolderConfig>) {
        viewModelScope.launch {
            try {
                val arr = org.json.JSONArray()
                for (f in list) {
                    val obj = org.json.JSONObject()
                    obj.put("uri", f.uri)
                    obj.put("alias", f.alias)
                    obj.put("path", f.path)
                    obj.put("pdfCount", f.pdfCount)
                    obj.put("cbzCount", f.cbzCount)
                    obj.put("totalBooks", f.totalBooks)
                    obj.put("totalBytes", f.totalBytes)
                    obj.put("lastScanTime", f.lastScanTime)
                    obj.put("permissionGranted", f.permissionGranted)
                    arr.put(obj)
                }
                repository.setPreference("folder_configs", arr.toString())
                _foldersList.value = list
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to save folders list", e)
            }
        }
    }

    fun addFolder(uri: String, alias: String) {
        viewModelScope.launch {
            val currentList = _foldersList.value.toMutableList()
            if (currentList.any { it.uri == uri }) {
                setActiveFolder(uri)
                return@launch
            }
            val path = getPathDisplayName(uri)
            val newFolder = FolderConfig(
                uri = uri,
                alias = alias,
                path = path,
                permissionGranted = true
            )
            currentList.add(newFolder)
            saveFoldersList(currentList)
            setActiveFolder(uri)
            scanFolder(uri)
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            val currentList = _foldersList.value.filter { it.uri != uri }
            saveFoldersList(currentList)
            
            if (_activeFolderUri.value == uri) {
                val nextActive = if (currentList.isNotEmpty()) currentList.first().uri else ""
                setActiveFolder(nextActive)
            }
            
            val books = repository.allBooks.firstOrNull() ?: emptyList()
            for (b in books) {
                if (b.id.startsWith(uri)) {
                    repository.deleteBookFromLibrary(b.id)
                }
            }
        }
    }

    fun renameFolderAlias(uri: String, newAlias: String) {
        viewModelScope.launch {
            val updated = _foldersList.value.map { f ->
                if (f.uri == uri) f.copy(alias = newAlias) else f
            }
            saveFoldersList(updated)
        }
    }

    fun setActiveFolder(uri: String) {
        viewModelScope.launch {
            repository.setPreference("active_folder_uri", uri)
            _activeFolderUri.value = uri
            
            if (uri.isNotEmpty() && uri != "all") {
                _selectedFolderUri.value = uri
                repository.setPreference(BookRepository.KEY_SELECTED_FOLDER_URI, uri)
                
                // Update recent folders
                val current = _recentFolders.value.toMutableList()
                current.remove(uri)
                current.add(0, uri)
                val trimmed = current.take(5)
                _recentFolders.value = trimmed
                try {
                    val arr = org.json.JSONArray()
                    for (u in trimmed) {
                        arr.put(u)
                    }
                    repository.setPreference("recent_folders", arr.toString())
                } catch (e: Exception) {
                    Log.e("BookViewModel", "Failed to save recent folders", e)
                }
            }
        }
    }

    fun scanFolder(uriStr: String) {
        viewModelScope.launch {
            repository.scanFolder(uriStr)
            
            val allBooks = repository.allBooks.firstOrNull() ?: emptyList()
            val folderBooks = allBooks.filter { it.id.startsWith(uriStr) }
            val pdfCount = folderBooks.count { it.format == "pdf" }
            val cbzCount = folderBooks.count { it.format == "cbz" }
            val totalBooks = folderBooks.size
            val totalBytes = folderBooks.sumOf { it.fileSize }
            
            val updated = _foldersList.value.map { f ->
                if (f.uri == uriStr) {
                    f.copy(
                        pdfCount = pdfCount,
                        cbzCount = cbzCount,
                        totalBooks = totalBooks,
                        totalBytes = totalBytes,
                        lastScanTime = System.currentTimeMillis()
                    )
                } else f
            }
            saveFoldersList(updated)
        }
    }

    fun scanAllFolders() {
        viewModelScope.launch {
            for (f in _foldersList.value) {
                scanFolder(f.uri)
            }
        }
    }

    // --- SETTINGS BACKUP & DEFAULTS ---
    fun exportSettingsToJson(): String {
        return try {
            val obj = org.json.JSONObject()
            obj.put("accent_color", _accentColorSetting.value)
            obj.put("material_you", _materialYouSetting.value)
            obj.put("corner_style", _cornerStyleSetting.value)
            obj.put("ui_font_size", _uiFontSizeSetting.value)
            obj.put("reader_font_size", _readerFontSizeSetting.value)
            obj.put("icons_style", _iconsStyleSetting.value)
            obj.put("layout_spacing", _layoutSpacingSetting.value)
            obj.put("navigation_style", _navigationStyleSetting.value)
            obj.put("animations_level", _animationsLevelSetting.value)
            obj.put("app_language", _appLanguageSetting.value)
            obj.put("volume_keys_page_turn", _volumeKeysPageTurn.value)
            obj.put("keep_screen_on", _keepScreenOn.value)
            obj.put("dark_mode", _darkModeSetting.value)
            obj.toString(2)
        } catch (e: Exception) {
            Log.e("BookViewModel", "Failed to export settings", e)
            ""
        }
    }

    fun importSettingsFromJson(json: String): Boolean {
        return try {
            val obj = org.json.JSONObject(json)
            setAccentColor(obj.optString("accent_color", "Teal"))
            setMaterialYou(obj.optBoolean("material_you", false))
            setCornerStyle(obj.optString("corner_style", "rounded"))
            setUiFontSize(obj.optString("ui_font_size", "default"))
            setReaderFontSize(obj.optString("reader_font_size", "default"))
            setIconsStyle(obj.optString("icons_style", "filled"))
            setLayoutSpacing(obj.optString("layout_spacing", "comfortable"))
            setNavigationStyle(obj.optString("navigation_style", "bottom_nav"))
            setAnimationsLevel(obj.optString("animations_level", "full"))
            setAppLanguage(obj.optString("app_language", "en"))
            setVolumeKeysPageTurn(obj.optBoolean("volume_keys_page_turn", false))
            setKeepScreenOn(obj.optBoolean("keep_screen_on", false))
            setDarkMode(obj.optString("dark_mode", "dark"))
            true
        } catch (e: Exception) {
            Log.e("BookViewModel", "Failed to import settings", e)
            false
        }
    }

    fun resetAppearanceSettings() {
        setAccentColor("Teal")
        setMaterialYou(false)
        setCornerStyle("rounded")
        setUiFontSize("default")
        setIconsStyle("filled")
        setLayoutSpacing("comfortable")
        setAnimationsLevel("full")
    }

    fun restoreDefaultSettings() {
        resetAppearanceSettings()
        setReaderFontSize("default")
        setNavigationStyle("bottom_nav")
        setAppLanguage("en")
        setVolumeKeysPageTurn(false)
        setKeepScreenOn(false)
        setDarkMode("dark")
    }
}

data class FolderConfig(
    val uri: String,
    val alias: String,
    val path: String,
    val pdfCount: Int = 0,
    val cbzCount: Int = 0,
    val totalBooks: Int = 0,
    val totalBytes: Long = 0,
    val lastScanTime: Long = 0,
    val permissionGranted: Boolean = true
)

data class FolderStats(
    val folderName: String,
    val totalBooks: Int,
    val pdfCount: Int,
    val cbzCount: Int,
    val totalBytes: Long,
    val totalReadingTimeMinutes: Long
)
