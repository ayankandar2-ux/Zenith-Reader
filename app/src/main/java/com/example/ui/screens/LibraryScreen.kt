package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.BookEntity
import com.example.ui.viewmodel.BookViewModel
import com.example.ui.viewmodel.FolderStats
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: BookViewModel,
    onBookSelected: (BookEntity) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val books by viewModel.booksState.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val filterBy by viewModel.filterBy.collectAsState()

    val isMultiSelectActive by viewModel.isMultiSelectActive.collectAsState()
    val selectedBookIds by viewModel.selectedBookIds.collectAsState()

    val appLang by viewModel.appLanguageSetting.collectAsState()
    val foldersList by viewModel.foldersList.collectAsState()
    val activeFolderUri by viewModel.activeFolderUri.collectAsState()
    val recentFolders by viewModel.recentFolders.collectAsState()

    fun getString(key: String): String = com.example.util.Localization.getString(appLang, key)

    val context = LocalContext.current
    var folderUriToUpdatePermission by remember { mutableStateOf<String?>(null) }
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "Failed to take persistable permission", e)
            }
            
            val targetUri = folderUriToUpdatePermission
            if (targetUri != null) {
                val updatedList = foldersList.map { f ->
                    if (f.uri == targetUri) {
                        f.copy(uri = it.toString(), path = viewModel.getPathDisplayName(it.toString()), permissionGranted = true)
                    } else f
                }
                viewModel.saveFoldersList(updatedList)
                folderUriToUpdatePermission = null
            } else {
                val alias = viewModel.getPathDisplayName(it.toString())
                viewModel.addFolder(it.toString(), alias)
            }
        }
    }

    var showStatsDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var activePreviewBook by remember { mutableStateOf<BookEntity?>(null) }
    var isDashboardExpanded by remember { mutableStateOf(false) }
    
    var showRenameDialogForFolder by remember { mutableStateOf<com.example.ui.viewmodel.FolderConfig?>(null) }
    var newAliasText by remember { mutableStateOf("") }
    var showInfoDialogForFolder by remember { mutableStateOf<com.example.ui.viewmodel.FolderConfig?>(null) }
    
    var currentTab by remember { mutableStateOf("library") } // "library", "history", "stats"

    Scaffold(
        topBar = {
            if (isMultiSelectActive) {
                TopAppBar(
                    title = { Text("${selectedBookIds.size} Selected", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, "Cancel Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.deleteSelectedFromLibrary() }) {
                            Icon(Icons.Default.DeleteSweep, "Remove from Library", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        if (currentTab == "library") {
                            var showFolderSwitcher by remember { mutableStateOf(false) }
                            val activeFolder = foldersList.find { it.uri == activeFolderUri }
                            val folderDisplayName = activeFolder?.alias ?: "All Library"
                            
                            Row(
                                modifier = Modifier
                                    .clickable { showFolderSwitcher = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = folderDisplayName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Library Folder",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            if (showFolderSwitcher) {
                                AlertDialog(
                                    onDismissRequest = { showFolderSwitcher = false },
                                    title = { Text(getString("folder_switcher"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                                    text = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.verticalScroll(rememberScrollState())
                                        ) {
                                            // Quick actions row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        folderPickerLauncher.launch(null)
                                                        showFolderSwitcher = false
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Folder", style = MaterialTheme.typography.labelMedium)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.scanAllFolders()
                                                        showFolderSwitcher = false
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Scan All", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            
                                            HorizontalDivider()
                                            
                                            // Current active section title
                                            Text(
                                                text = "CURRENT ACTIVE FOLDER",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            // Render active item
                                            val isAllActive = activeFolderUri.isEmpty()
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isAllActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .clickable {
                                                        viewModel.setActiveFolder("")
                                                        showFolderSwitcher = false
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(selected = isAllActive, onClick = {
                                                    viewModel.setActiveFolder("")
                                                    showFolderSwitcher = false
                                                })
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(text = "All Library", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                                    Text(text = "Show books from all added folders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            
                                            // Recently Used Folders section if applicable
                                            val recentMatchedFolders = foldersList.filter { recentFolders.contains(it.uri) && it.uri != activeFolderUri }
                                            if (recentMatchedFolders.isNotEmpty()) {
                                                HorizontalDivider()
                                                Text(
                                                    text = "RECENTLY USED FOLDERS",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                recentMatchedFolders.take(3).forEach { f ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                            .clickable {
                                                                viewModel.setActiveFolder(f.uri)
                                                                showFolderSwitcher = false
                                                            }
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.History, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column {
                                                            Text(text = f.alias, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                                            Text(text = f.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            HorizontalDivider()
                                            
                                            // All Registered Folders section
                                            Text(
                                                text = "ALL ADDED FOLDERS",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            foldersList.forEach { f ->
                                                val isSelected = activeFolderUri == f.uri
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                                        .clickable {
                                                            viewModel.setActiveFolder(f.uri)
                                                            showFolderSwitcher = false
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(selected = isSelected, onClick = {
                                                        viewModel.setActiveFolder(f.uri)
                                                        showFolderSwitcher = false
                                                    })
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = f.alias, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                                        Text(text = f.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(
                                                            text = "${f.totalBooks} Books (${f.pdfCount} PDFs, ${f.cbzCount} CBZs)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    
                                                    var showFolderOptions by remember { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(onClick = { showFolderOptions = true }) {
                                                            Icon(Icons.Default.MoreVert, "Folder Options")
                                                        }
                                                        DropdownMenu(
                                                            expanded = showFolderOptions,
                                                            onDismissRequest = { showFolderOptions = false }
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("Scan Folder") },
                                                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                                                onClick = {
                                                                    viewModel.scanFolder(f.uri)
                                                                    showFolderOptions = false
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Rename Alias") },
                                                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                                onClick = {
                                                                    newAliasText = f.alias
                                                                    showRenameDialogForFolder = f
                                                                    showFolderOptions = false
                                                                    showFolderSwitcher = false
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Remove Folder") },
                                                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                                                onClick = {
                                                                    viewModel.removeFolder(f.uri)
                                                                    showFolderOptions = false
                                                                    showFolderSwitcher = false
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Folder Info") },
                                                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                                                onClick = {
                                                                    showInfoDialogForFolder = f
                                                                    showFolderOptions = false
                                                                    showFolderSwitcher = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            currentTab = "folders"
                                            showFolderSwitcher = false
                                        }) {
                                            Text("Manage Folders Screen")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showFolderSwitcher = false }) {
                                            Text("Close")
                                        }
                                    }
                                )
                            }
                        } else {
                            Text(
                                text = when (currentTab) {
                                    "history" -> getString("history")
                                    "folders" -> "Manage Folders"
                                    else -> "Statistics"
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    actions = {
                        if (currentTab == "library") {
                            // Grid/List toggle
                            IconButton(onClick = { viewModel.setGridView(!isGridView) }) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle layout"
                                )
                            }
                            // Sort selector
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            // Folder stats dialog
                            IconButton(onClick = { showStatsDialog = true }) {
                                Icon(Icons.Default.Analytics, "Library Statistics")
                            }
                        }
                        
                        // Top-right overflow menu
                        var showOverflowMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More Options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Manage Folders") },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                    onClick = {
                                        currentTab = "folders"
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = {
                                        onNavigateToSettings()
                                        showOverflowMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == "library",
                    onClick = { currentTab = "library" },
                    icon = { Icon(Icons.Default.LibraryBooks, null) },
                    label = { Text(getString("library")) }
                )
                NavigationBarItem(
                    selected = currentTab == "history",
                    onClick = { currentTab = "history" },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text(getString("history")) }
                )
                NavigationBarItem(
                    selected = currentTab == "folders",
                    onClick = { currentTab = "folders" },
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("Folders") }
                )
                NavigationBarItem(
                    selected = currentTab == "stats",
                    onClick = { currentTab = "stats" },
                    icon = { Icon(Icons.Default.Analytics, null) },
                    label = { Text("Statistics") }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == "library") {
                FloatingActionButton(
                    onClick = { viewModel.runScan() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("refresh_library_fab")
                ) {
                    Icon(Icons.Default.Refresh, "Refresh Library")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "library" -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val stats = viewModel.getFolderStatistics()

            // Dynamic Collapsible Stats Dashboard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Reading Habit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reading Activity Summary",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(
                            onClick = { isDashboardExpanded = !isDashboardExpanded },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isDashboardExpanded) "Hide Details" else "View Stats",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isDashboardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isDashboardExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DashboardStatItem(
                                icon = Icons.Default.LibraryBooks,
                                value = "${stats.totalBooks}",
                                label = "Total Books"
                            )
                            DashboardStatItem(
                                icon = Icons.Default.Schedule,
                                value = "${stats.totalReadingTimeMinutes}m",
                                label = "Time Read"
                            )
                            DashboardStatItem(
                                icon = Icons.Default.Star,
                                value = "${books.count { it.isFavorite }}",
                                label = "Favorites"
                            )
                            DashboardStatItem(
                                icon = Icons.Default.CheckCircle,
                                value = "${books.count { it.lastPage >= it.pageCount - 1 && it.pageCount > 0 }}",
                                label = "Finished"
                            )
                        }
                    } else {
                        // Compact single-line summary
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Catalog holds ${stats.pdfCount} PDFs and ${stats.cbzCount} CBZs spanning ${formatFileSize(stats.totalBytes)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Instant Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(getString("search_placeholder")) },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear Search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_text_input")
            )

            // Scanning progress indicator
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scanProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Filters (Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterBy == BookViewModel.FilterType.ALL,
                    onClick = { viewModel.setFilterBy(BookViewModel.FilterType.ALL) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterBy == BookViewModel.FilterType.FAVORITES,
                    onClick = { viewModel.setFilterBy(BookViewModel.FilterType.FAVORITES) },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterBy == BookViewModel.FilterType.CONTINUE_READING,
                    onClick = { viewModel.setFilterBy(BookViewModel.FilterType.CONTINUE_READING) },
                    label = { Text("Reading") },
                    leadingIcon = { Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterBy == BookViewModel.FilterType.COMPLETED,
                    onClick = { viewModel.setFilterBy(BookViewModel.FilterType.COMPLETED) },
                    label = { Text("Completed") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Book List/Grid representation
            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Empty Library",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No results found" else "Your Library is Empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching for something else" else "Check your folder or add some PDF & CBZ files, then refresh",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (searchQuery.isEmpty()) {
                            Button(
                                onClick = { viewModel.runScan() },
                                modifier = Modifier.padding(top = 20.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Demo PDF & CBZ")
                            }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (searchQuery.isEmpty() && filterBy == BookViewModel.FilterType.ALL) {
                            val unfinishedBooks = books.filter { it.lastOpened > 0 && (it.pageCount <= 0 || it.lastPage < it.pageCount - 1) }
                            val continueReadingBook = unfinishedBooks.maxByOrNull { it.lastOpened }
                            val recentlyOpenedBooks = books.filter { it.lastOpened > 0 }.sortedByDescending { it.lastOpened }.take(30)

                            continueReadingBook?.let { cr ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ContinueReadingSection(book = cr, onClick = { onBookSelected(cr) })
                                }
                            }

                            if (recentlyOpenedBooks.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    RecentlyOpenedSection(books = recentlyOpenedBooks, onBookClick = onBookSelected)
                                }
                            }
                        }

                        items(books, key = { it.id }) { book ->
                            BookGridItem(
                                book = book,
                                isSelected = selectedBookIds.contains(book.id),
                                onSelectToggle = { viewModel.toggleBookSelection(book.id) },
                                onLongClick = { viewModel.toggleBookSelection(book.id) },
                                onClick = {
                                    if (isMultiSelectActive) {
                                        viewModel.toggleBookSelection(book.id)
                                    } else {
                                        onBookSelected(book)
                                    }
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(book) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (searchQuery.isEmpty() && filterBy == BookViewModel.FilterType.ALL) {
                            val unfinishedBooks = books.filter { it.lastOpened > 0 && (it.pageCount <= 0 || it.lastPage < it.pageCount - 1) }
                            val continueReadingBook = unfinishedBooks.maxByOrNull { it.lastOpened }
                            val recentlyOpenedBooks = books.filter { it.lastOpened > 0 }.sortedByDescending { it.lastOpened }.take(30)

                            continueReadingBook?.let { cr ->
                                item {
                                    ContinueReadingSection(book = cr, onClick = { onBookSelected(cr) })
                                }
                            }

                            if (recentlyOpenedBooks.isNotEmpty()) {
                                item {
                                    RecentlyOpenedSection(books = recentlyOpenedBooks, onBookClick = onBookSelected)
                                }
                            }
                        }

                        items(books, key = { it.id }) { book ->
                            BookListItem(
                                book = book,
                                isSelected = selectedBookIds.contains(book.id),
                                onSelectToggle = { viewModel.toggleBookSelection(book.id) },
                                onLongClick = { viewModel.toggleBookSelection(book.id) },
                                onClick = {
                                    if (isMultiSelectActive) {
                                        viewModel.toggleBookSelection(book.id)
                                    } else {
                                        activePreviewBook = book
                                    }
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(book) },
                                onDeleteFromLibrary = { viewModel.deleteSingleBookFromLibrary(book.id) }
                            )
                        }
                    }
                }
            }
                    }
                }
                "history" -> {
                    ReadingHistoryTab(
                        books = books,
                        viewModel = viewModel,
                        onBookClick = onBookSelected
                    )
                }
                "folders" -> {
                    ManageFoldersTab(
                        viewModel = viewModel,
                        foldersList = foldersList,
                        activeFolderUri = activeFolderUri,
                        onNavigateToLibrary = { currentTab = "library" },
                        onReRequestPermission = { uri ->
                            folderUriToUpdatePermission = uri
                            folderPickerLauncher.launch(null)
                        }
                    )
                }
                "stats" -> {
                    StatisticsTab(
                        books = books,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Sort Dropdown Menu Dialog
    if (showSortMenu) {
        AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { Text("Sort Books By") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortMenuItem(
                        text = "Title (A-Z)",
                        selected = sortBy == BookViewModel.SortType.TITLE,
                        onClick = { viewModel.setSortBy(BookViewModel.SortType.TITLE); showSortMenu = false }
                    )
                    SortMenuItem(
                        text = "Date Added",
                        selected = sortBy == BookViewModel.SortType.DATE_ADDED,
                        onClick = { viewModel.setSortBy(BookViewModel.SortType.DATE_ADDED); showSortMenu = false }
                    )
                    SortMenuItem(
                        text = "Last Opened",
                        selected = sortBy == BookViewModel.SortType.LAST_OPENED,
                        onClick = { viewModel.setSortBy(BookViewModel.SortType.LAST_OPENED); showSortMenu = false }
                    )
                    SortMenuItem(
                        text = "File Size",
                        selected = sortBy == BookViewModel.SortType.FILE_SIZE,
                        onClick = { viewModel.setSortBy(BookViewModel.SortType.FILE_SIZE); showSortMenu = false }
                    )
                    SortMenuItem(
                        text = "Reading Progress",
                        selected = sortBy == BookViewModel.SortType.PROGRESS,
                        onClick = { viewModel.setSortBy(BookViewModel.SortType.PROGRESS); showSortMenu = false }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortMenu = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Stats Dialog
    if (showStatsDialog) {
        val stats = viewModel.getFolderStatistics()
        FolderStatsDialog(stats = stats, onDismiss = { showStatsDialog = false })
    }

    // Interactive Premium Book Detail Bottom Sheet
    if (activePreviewBook != null) {
        val book = activePreviewBook!!
        val progress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
        val progressPercentage = (progress * 100).toInt().coerceIn(0, 100)

        ModalBottomSheet(
            onDismissRequest = { activePreviewBook = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left Cover Thumbnail
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val context = LocalContext.current
                        val coverFile = File(context.cacheDir, "book_covers/${book.id.hashCode()}.png")
                        if (coverFile.exists() && coverFile.length() > 0) {
                            AsyncImage(
                                model = coverFile,
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (book.format == "pdf") Color(0xFFE57373) else Color(0xFF64B5F6)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = book.format.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Right Details Panel
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = book.format.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier
                                    .background(
                                        color = if (book.format == "pdf") Color(0xFFC62828) else Color(0xFF1565C0),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (book.isFavorite) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Favorite",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${book.pageCount} pages  •  ${formatFileSize(book.fileSize)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reading Progress",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$progressPercentage% Completed",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Current Position:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Page ${book.lastPage + 1} of ${book.pageCount}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        if (book.readingTime > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val minutes = book.readingTime / (1000 * 60)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Time Invested:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${minutes} minutes read",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }

                        if (book.lastOpened > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Last Opened:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDate(book.lastOpened),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite Toggle button
                    OutlinedIconToggleButton(
                        checked = book.isFavorite,
                        onCheckedChange = {
                            viewModel.toggleFavorite(book)
                            activePreviewBook = book.copy(isFavorite = !book.isFavorite)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (book.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Main Start / Resume button
                    Button(
                        onClick = {
                            val targetBook = activePreviewBook!!
                            activePreviewBook = null
                            onBookSelected(targetBook)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = if (book.lastPage > 0) Icons.Default.PlayArrow else Icons.Default.MenuBook,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (book.lastPage > 0) "Resume Reading" else "Start Reading",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Rename Dialog
    if (showRenameDialogForFolder != null) {
        val folder = showRenameDialogForFolder!!
        AlertDialog(
            onDismissRequest = { showRenameDialogForFolder = null },
            title = { Text("Rename Folder Alias") },
            text = {
                OutlinedTextField(
                    value = newAliasText,
                    onValueChange = { newAliasText = it },
                    label = { Text("Alias Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newAliasText.trim().isNotEmpty()) {
                        viewModel.renameFolderAlias(folder.uri, newAliasText.trim())
                    }
                    showRenameDialogForFolder = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Folder Information Dialog
    if (showInfoDialogForFolder != null) {
        val folder = showInfoDialogForFolder!!
        val formattedSize = remember(folder.totalBytes) {
            val df = java.text.DecimalFormat("#.##")
            val sizeKb = folder.totalBytes / 1024f
            val sizeMb = sizeKb / 1024f
            val sizeGb = sizeMb / 1024f
            when {
                sizeGb >= 1.0f -> "${df.format(sizeGb)} GB"
                sizeMb >= 1.0f -> "${df.format(sizeMb)} MB"
                else -> "${df.format(sizeKb)} KB"
            }
        }
        val scanTimeStr = remember(folder.lastScanTime) {
            if (folder.lastScanTime <= 0L) "Never"
            else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(folder.lastScanTime))
        }
        AlertDialog(
            onDismissRequest = { showInfoDialogForFolder = null },
            title = { Text("Folder Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alias: ${folder.alias}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Path: ${folder.path}", style = MaterialTheme.typography.bodyMedium)
                    Text("URI: ${folder.uri}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Books:")
                        Text("${folder.totalBooks}", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("PDF Documents:")
                        Text("${folder.pdfCount}", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CBZ Comics:")
                        Text("${folder.cbzCount}", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size on Disk:")
                        Text(formattedSize, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Scanned:")
                        Text(scanTimeStr, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Permission Status:")
                        Text(
                            text = if (folder.permissionGranted) "Granted" else "Missing",
                            color = if (folder.permissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showInfoDialogForFolder = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SortMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(
    book: BookEntity,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val progress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
    val progressPercentage = (progress * 100).toInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_item_grid"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            // Thumbnail Cover using Coil loading Cached cover png
            val context = LocalContext.current
            val coverFile = File(context.cacheDir, "book_covers/${book.id.hashCode()}.png")
            if (coverFile.exists() && coverFile.length() > 0) {
                AsyncImage(
                    model = coverFile,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Beautiful procedural placeholder icon for files missing generated images
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (book.format == "pdf") Color(0xFFE57373) else Color(0xFF64B5F6)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (book.format == "pdf") Icons.Default.Description else Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.format.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Top overlay bar for selection and favorites
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (book.isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Bottom progress bar overlay
            if (progressPercentage > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // Title and Format Pill
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${book.pageCount} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatFileSize(book.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: BookEntity,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteFromLibrary: () -> Unit
) {
    val progress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
    val progressPercentage = (progress * 100).toInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_item_list"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .size(70.dp, 100.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val context = LocalContext.current
                val coverFile = File(context.cacheDir, "book_covers/${book.id.hashCode()}.png")
                if (coverFile.exists() && coverFile.length() > 0) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (book.format == "pdf") Color(0xFFE57373) else Color(0xFF64B5F6)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.format.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = book.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (book.format == "pdf") Color(0xFFC62828) else Color(0xFF1565C0)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${book.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(book.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Progress Bar and reading time details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "$progressPercentage%",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (book.readingTime > 0) {
                    val minutes = book.readingTime / (1000 * 60)
                    Text(
                        text = "Read for ${minutes}m • Last: ${formatDate(book.lastOpened)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action triggers
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (book.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Option menu / remove from library button
                var showRowMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showRowMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More Options")
                    }
                    DropdownMenu(expanded = showRowMenu, onDismissRequest = { showRowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete from library database") },
                            onClick = {
                                onDeleteFromLibrary()
                                showRowMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderStatsDialog(
    stats: FolderStats,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Folder Statistics",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "/${stats.folderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                StatsRow(label = "Total Books Found", value = "${stats.totalBooks}")
                StatsRow(label = "PDF Documents", value = "${stats.pdfCount}")
                StatsRow(label = "CBZ Comics / Manga", value = "${stats.cbzCount}")
                StatsRow(label = "Total Size On Disk", value = formatFileSize(stats.totalBytes))
                StatsRow(label = "Total Reading Time", value = "${stats.totalReadingTimeMinutes} minutes")

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
}

// Helpers
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return format.format(date)
}

@Composable
fun DashboardStatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BookCoverImage(
    book: BookEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverFile = remember(book.id) { File(context.cacheDir, "book_covers/${book.id.hashCode()}.png") }
    if (coverFile.exists() && coverFile.length() > 0) {
        AsyncImage(
            model = coverFile,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .background(
                    if (book.format == "pdf") Color(0xFFE57373) else Color(0xFF64B5F6)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = book.format.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun ContinueReadingSection(
    book: BookEntity,
    onClick: () -> Unit
) {
    val progress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
    val progressPercentage = (progress * 100).toInt().coerceIn(0, 100)
    
    val lastReadText = remember(book.lastOpened) {
        val diff = System.currentTimeMillis() - book.lastOpened
        when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(book.lastOpened))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onClick() }
            .testTag("continue_reading_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Book cover
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                BookCoverImage(book = book, modifier = Modifier.fillMaxSize())
            }

            // Info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONTINUE READING",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = lastReadText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (book.pageCount > 0) "Page ${book.lastPage + 1} of ${book.pageCount}" else "Page ${book.lastPage + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$progressPercentage%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentlyOpenedSection(
    books: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit
) {
    if (books.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Recently Opened",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(books, key = { "recent_${it.id}" }) { book ->
                val progress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
                Card(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { onBookClick(book) }
                        .testTag("recent_book_card_${book.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column {
                        // Cover thumbnail
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        ) {
                            BookCoverImage(book = book, modifier = Modifier.fillMaxSize())
                        }

                        // Progress bar at the bottom of thumbnail
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )

                        // Title
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingHistoryTab(
    books: List<BookEntity>,
    viewModel: BookViewModel,
    onBookClick: (BookEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterBy by remember { mutableStateOf("all") } // "all", "pdf", "cbz", "favorites", "completed", "unfinished"
    var sortBy by remember { mutableStateOf("last_opened") } // "last_opened", "reading_time", "progress", "title", "date_added"
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var isMultiSelect by remember { mutableStateOf(false) }

    // Export/Import backup dialog
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupJsonText by remember { mutableStateOf("") }
    var importStatusMsg by remember { mutableStateOf("") }

    // Sort dialog
    var showHistorySortMenu by remember { mutableStateOf(false) }

    // Process list
    var filtered = books.filter { it.lastOpened > 0 }
    
    if (searchQuery.isNotEmpty()) {
        filtered = filtered.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    filtered = when (filterBy) {
        "pdf" -> filtered.filter { it.format == "pdf" }
        "cbz" -> filtered.filter { it.format == "cbz" }
        "favorites" -> filtered.filter { it.isFavorite }
        "completed" -> filtered.filter { it.lastPage >= it.pageCount - 1 && it.pageCount > 0 }
        "unfinished" -> filtered.filter { it.lastPage < it.pageCount - 1 && it.pageCount > 0 }
        else -> filtered
    }

    filtered = when (sortBy) {
        "last_opened" -> filtered.sortedByDescending { it.lastOpened }
        "reading_time" -> filtered.sortedByDescending { it.readingTime }
        "progress" -> filtered.sortedByDescending { if (it.pageCount > 0) it.lastPage.toFloat() / it.pageCount else 0f }
        "title" -> filtered.sortedBy { it.title.lowercase() }
        "date_added" -> filtered.sortedByDescending { it.dateAdded }
        else -> filtered.sortedByDescending { it.lastOpened }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search & Backup actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search reading history...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                backupJsonText = viewModel.exportHistoryToJson()
                importStatusMsg = ""
                showBackupDialog = true
            }) {
                Icon(Icons.Default.Backup, "Export/Import Backup")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filterBy == "all",
                            onClick = { filterBy = "all" },
                            label = { Text("All History") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterBy == "pdf",
                            onClick = { filterBy = "pdf" },
                            label = { Text("PDFs") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterBy == "cbz",
                            onClick = { filterBy = "cbz" },
                            label = { Text("CBZs") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterBy == "favorites",
                            onClick = { filterBy = "favorites" },
                            label = { Text("Favorites") },
                            leadingIcon = { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterBy == "completed",
                            onClick = { filterBy = "completed" },
                            label = { Text("Completed") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterBy == "unfinished",
                            onClick = { filterBy = "unfinished" },
                            label = { Text("Unfinished") }
                        )
                    }
                }
            }

            IconButton(onClick = { showHistorySortMenu = true }) {
                Icon(Icons.Default.Sort, "Sort History")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-select actions bar
        if (isMultiSelect) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} Selected")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selectedIds = emptySet()
                            isMultiSelect = false
                        }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.resetBooksHistory(selectedIds.toList())
                                selectedIds = emptySet()
                                isMultiSelect = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset Progress")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            if (filtered.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        viewModel.resetAllBooksHistory()
                    }) {
                        Icon(Icons.Default.DeleteForever, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All History", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // History items list
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No history found matching filters", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { "history_item_${it.id}" }) { book ->
                    HistoryListItem(
                        book = book,
                        isSelected = selectedIds.contains(book.id),
                        isMultiSelectActive = isMultiSelect,
                        onSelectToggle = {
                            val next = selectedIds.toMutableSet()
                            if (next.contains(book.id)) {
                                next.remove(book.id)
                            } else {
                                next.add(book.id)
                            }
                            selectedIds = next
                            isMultiSelect = next.isNotEmpty()
                        },
                        onClick = {
                            if (isMultiSelect) {
                                val next = selectedIds.toMutableSet()
                                if (next.contains(book.id)) {
                                    next.remove(book.id)
                                } else {
                                    next.add(book.id)
                                }
                                selectedIds = next
                                isMultiSelect = next.isNotEmpty()
                            } else {
                                onBookClick(book)
                            }
                        },
                        onDeleteHistory = {
                            viewModel.resetBookHistory(book.id)
                        }
                    )
                }
            }
        }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup History (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copy this JSON block to save history, or paste/modify it to restore progress across devices.", style = MaterialTheme.typography.bodySmall)
                    
                    OutlinedTextField(
                        value = backupJsonText,
                        onValueChange = { backupJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    if (importStatusMsg.isNotEmpty()) {
                        Text(importStatusMsg, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val success = viewModel.importHistoryFromJson(backupJsonText)
                    importStatusMsg = if (success) "Imported successfully! Refresh to see changes." else "Invalid backup JSON format."
                }) {
                    Text("Import/Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showHistorySortMenu) {
        AlertDialog(
            onDismissRequest = { showHistorySortMenu = false },
            title = { Text("Sort Reading History") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortMenuItem(
                        text = "Last Opened",
                        selected = sortBy == "last_opened",
                        onClick = { sortBy = "last_opened"; showHistorySortMenu = false }
                    )
                    SortMenuItem(
                        text = "Total Reading Time",
                        selected = sortBy == "reading_time",
                        onClick = { sortBy = "reading_time"; showHistorySortMenu = false }
                    )
                    SortMenuItem(
                        text = "Reading Progress",
                        selected = sortBy == "progress",
                        onClick = { sortBy = "progress"; showHistorySortMenu = false }
                    )
                    SortMenuItem(
                        text = "Title (A-Z)",
                        selected = sortBy == "title",
                        onClick = { sortBy = "title"; showHistorySortMenu = false }
                    )
                    SortMenuItem(
                        text = "Date Added",
                        selected = sortBy == "date_added",
                        onClick = { sortBy = "date_added"; showHistorySortMenu = false }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistorySortMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryListItem(
    book: BookEntity,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onDeleteHistory: () -> Unit
) {
    val progress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
    val progressPercentage = (progress * 100).toInt().coerceIn(0, 100)
    
    val dateText = remember(book.lastOpened) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(book.lastOpened))
    }

    val timeText = remember(book.readingTime) {
        val totalSec = book.readingTime / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onSelectToggle
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                BookCoverImage(book = book, modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                color = if (book.format == "pdf") Color(0xFFC62828) else Color(0xFF1565C0),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${book.lastPage + 1}/${book.pageCount} ($progressPercentage%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Time: $timeText",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sessions: ${book.readingSessions}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isMultiSelectActive) {
                        IconButton(
                            onClick = onDeleteHistory,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete entry from history",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsTab(
    books: List<BookEntity>,
    viewModel: BookViewModel
) {
    val totalBooks = books.size
    val openedBooks = books.filter { it.lastOpened > 0 }
    val totalOpened = openedBooks.size
    val completedBooks = openedBooks.filter { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
    val totalCompleted = completedBooks.size
    val totalUnfinished = totalOpened - totalCompleted
    val totalPagesRead = openedBooks.sumOf { it.lastPage + 1 }
    val totalReadingTimeMs = openedBooks.sumOf { it.readingTime }
    val totalSessions = openedBooks.sumOf { it.readingSessions }
    
    val totalReadingTimeMin = totalReadingTimeMs / (1000 * 60)
    
    val averageSessionMin = if (totalSessions > 0) {
        (totalReadingTimeMs / totalSessions) / (1000 * 60)
    } else {
        0L
    }

    val mostReadBook = openedBooks.maxByOrNull { it.readingTime }

    val streakStats = remember(openedBooks) { calculateStreakStats(books) }

    val now = System.currentTimeMillis()
    val oneWeekAgo = now - 7L * 24 * 60 * 60 * 1000
    val oneMonthAgo = now - 30L * 24 * 60 * 60 * 1000
    val oneYearAgo = now - 365L * 24 * 60 * 60 * 1000

    val readThisWeek = openedBooks.count { it.lastOpened >= oneWeekAgo }
    val readThisMonth = openedBooks.count { it.lastOpened >= oneMonthAgo }
    val readThisYear = openedBooks.count { it.lastOpened >= oneYearAgo }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Your Reading Insights",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Current Reading Streak",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${streakStats.currentStreak}",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (streakStats.currentStreak == 1) " day" else " days",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        text = "Longest streak: ${streakStats.longestStreak} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                Text(
                    text = "🔥",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Total Books",
                    value = "$totalBooks",
                    subtext = "$totalOpened opened",
                    icon = Icons.Default.LibraryBooks,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Books Read",
                    value = "$totalCompleted",
                    subtext = "$totalUnfinished unfinished",
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Time Spent",
                    value = "${totalReadingTimeMin}m",
                    subtext = "Avg: ${averageSessionMin}m / sess",
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Pages Flipped",
                    value = "$totalPagesRead",
                    subtext = "$totalSessions reading sessions",
                    icon = Icons.Default.ChromeReaderMode,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Recent Velocity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opened this week:", style = MaterialTheme.typography.bodyMedium)
                    Text("$readThisWeek books", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opened this month:", style = MaterialTheme.typography.bodyMedium)
                    Text("$readThisMonth books", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opened this year:", style = MaterialTheme.typography.bodyMedium)
                    Text("$readThisYear books", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        mostReadBook?.let { book ->
            val bookProgress = if (book.pageCount > 0) book.lastPage.toFloat() / (book.pageCount - 1).coerceAtLeast(1) else 0f
            val bookPercentage = (bookProgress * 100).toInt().coerceIn(0, 100)
            val readingMin = book.readingTime / (1000 * 60)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Most Read Book",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(92.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            BookCoverImage(book = book, modifier = Modifier.fillMaxSize())
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Total Time: ${readingMin} minutes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Flipped: Page ${book.lastPage + 1}/${book.pageCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$bookPercentage%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun calculateStreakStats(books: List<BookEntity>): StreakStats {
    val activeDays = books.filter { it.lastOpened > 0 }
        .map {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(it.lastOpened))
        }
        .toSet()
        .sorted()

    if (activeDays.isEmpty()) return StreakStats(0, 0)

    var longestStreak = 0
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayStr = sdf.format(Date())
    val yesterdayStr = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

    val hasReadRecently = activeDays.contains(todayStr) || activeDays.contains(yesterdayStr)

    var tempStreak = 0
    var previousDate: Date? = null

    for (dayStr in activeDays) {
        val currentDate = sdf.parse(dayStr) ?: continue
        if (previousDate == null) {
            tempStreak = 1
        } else {
            val diffMs = currentDate.time - previousDate.time
            val diffDays = diffMs / (1000 * 60 * 60 * 24)
            if (diffDays == 1L) {
                tempStreak++
            } else if (diffDays > 1L) {
                if (tempStreak > longestStreak) {
                    longestStreak = tempStreak
                }
                tempStreak = 1
            }
        }
        previousDate = currentDate
    }
    if (tempStreak > longestStreak) {
        longestStreak = tempStreak
    }

    var currentStreak = 0
    if (hasReadRecently) {
        var checkDate = Date()
        var streakCount = 0
        while (true) {
            val checkStr = sdf.format(checkDate)
            if (activeDays.contains(checkStr)) {
                streakCount++
                checkDate = Date(checkDate.time - 24 * 60 * 60 * 1000)
            } else {
                break
            }
        }
        if (streakCount == 0 && activeDays.contains(yesterdayStr)) {
            checkDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            while (true) {
                val checkStr = sdf.format(checkDate)
                if (activeDays.contains(checkStr)) {
                    streakCount++
                    checkDate = Date(checkDate.time - 24 * 60 * 60 * 1000)
                } else {
                    break
                }
            }
        }
        currentStreak = streakCount
    }

    if (currentStreak > longestStreak) {
        longestStreak = currentStreak
    }

    return StreakStats(currentStreak, longestStreak)
}

data class StreakStats(val currentStreak: Int, val longestStreak: Int)

@Composable
fun ManageFoldersTab(
    viewModel: BookViewModel,
    foldersList: List<com.example.ui.viewmodel.FolderConfig>,
    activeFolderUri: String,
    onNavigateToLibrary: () -> Unit,
    onReRequestPermission: (String) -> Unit
) {
    var showRenameDialogForFolder by remember { mutableStateOf<com.example.ui.viewmodel.FolderConfig?>(null) }
    var newAliasText by remember { mutableStateOf("") }
    var showDeleteConfirmDialogForFolder by remember { mutableStateOf<com.example.ui.viewmodel.FolderConfig?>(null) }
    
    // Create lazy column of folders
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Reading Directories",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Add, scan, and switch between your local book and comic directories.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (foldersList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "No folders added yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            items(foldersList, key = { it.uri }) { folder ->
                val isActive = folder.uri == activeFolderUri
                val isPermissionGranted = folder.permissionGranted
                
                val formattedSize = remember(folder.totalBytes) {
                    val df = java.text.DecimalFormat("#.##")
                    val sizeKb = folder.totalBytes / 1024f
                    val sizeMb = sizeKb / 1024f
                    val sizeGb = sizeMb / 1024f
                    when {
                        sizeGb >= 1.0f -> "${df.format(sizeGb)} GB"
                        sizeMb >= 1.0f -> "${df.format(sizeMb)} MB"
                        else -> "${df.format(sizeKb)} KB"
                    }
                }
                
                val scanTimeStr = remember(folder.lastScanTime) {
                    if (folder.lastScanTime <= 0L) "Never Scanned"
                    else "Scanned: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(folder.lastScanTime))
                }
                
                val cardShape = RoundedCornerShape(16.dp)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isActive) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), cardShape)
                            else Modifier
                        ),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    ),
                    shape = cardShape
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header row: Alias and active indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.alias,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = folder.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                
                                Surface(
                                    color = if (isPermissionGranted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (isPermissionGranted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isPermissionGranted) "GRANTED" else "NO ACCESS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider()
                        
                        // Book Counts & Stats Grid-like rows
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Books", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${folder.totalBooks}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PDFs / CBZs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${folder.pdfCount} / ${folder.cbzCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Storage Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedSize, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        // Last scan time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = scanTimeStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        HorizontalDivider()
                        
                        // Action row of buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Open / Set as Active
                            if (!isActive) {
                                Button(
                                    onClick = { 
                                        viewModel.setActiveFolder(folder.uri)
                                        onNavigateToLibrary()
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onNavigateToLibrary() },
                                    modifier = Modifier.weight(1.3f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Opened", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            
                            // Scan/Refresh
                            IconButton(
                                onClick = { viewModel.scanFolder(folder.uri) },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Refresh, "Scan/Refresh Folder")
                            }
                            
                            // Rename
                            IconButton(
                                onClick = { 
                                    newAliasText = folder.alias
                                    showRenameDialogForFolder = folder
                                }
                            ) {
                                Icon(Icons.Default.Edit, "Rename Alias")
                            }
                            
                            // Re-request permission if missing
                            if (!isPermissionGranted) {
                                IconButton(
                                    onClick = { onReRequestPermission(folder.uri) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.VpnKey, "Fix Permission")
                                }
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Delete
                            IconButton(
                                onClick = { showDeleteConfirmDialogForFolder = folder },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, "Remove Folder")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Rename Dialog
    if (showRenameDialogForFolder != null) {
        val folder = showRenameDialogForFolder!!
        AlertDialog(
            onDismissRequest = { showRenameDialogForFolder = null },
            title = { Text("Rename Folder Alias") },
            text = {
                OutlinedTextField(
                    value = newAliasText,
                    onValueChange = { newAliasText = it },
                    label = { Text("Alias Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newAliasText.trim().isNotEmpty()) {
                        viewModel.renameFolderAlias(folder.uri, newAliasText.trim())
                    }
                    showRenameDialogForFolder = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Confirm Dialog
    if (showDeleteConfirmDialogForFolder != null) {
        val folder = showDeleteConfirmDialogForFolder!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialogForFolder = null },
            title = { Text("Remove Folder", color = MaterialTheme.colorScheme.error) },
            text = {
                Text("Are you sure you want to remove the folder \"${folder.alias}\" from the app? The physical files will NOT be deleted, but books in this folder will be removed from your offline library.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeFolder(folder.uri)
                        showDeleteConfirmDialogForFolder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialogForFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
