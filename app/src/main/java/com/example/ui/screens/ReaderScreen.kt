package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.BookEntity
import com.example.ui.viewmodel.BookViewModel
import com.example.util.BookRenderer
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    viewModel: BookViewModel,
    onBack: () -> Unit
) {
    val book by viewModel.currentBook.collectAsState()
    val allBooks by viewModel.booksState.collectAsState()
    val bookmarks by viewModel.currentBookBookmarks.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

    // Local custom reader preferences
    val readerMode by viewModel.readerMode.collectAsState() // "vertical", "horizontal", "webtoon"
    val readerOrientation by viewModel.readerOrientation.collectAsState() // "sensor", "portrait", "landscape"
    val readerBrightness by viewModel.readerBrightness.collectAsState()
    var readerComfortFilter by remember { mutableStateOf("none") } // "none", "sepia", "invert"

    var isAutoScrolling by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableFloatStateOf(10f) }

    val context = LocalContext.current

    // Render pages at a resolution based on the actual device screen instead of a
    // hardcoded 1080x1920 cap, which needlessly downscaled (and blurred) pages on
    // higher-resolution devices. A 1.5x multiplier keeps some headroom for pinch-zoom
    // clarity, capped to avoid excessive memory use on very high-res displays.
    // IMPORTANT: this exact value must be used for both prefetching and the actual
    // display load below - the page cache key includes width/height, so a mismatch
    // would make prefetching a no-op (cache miss on the "real" load).
    val displayMetrics = context.resources.displayMetrics
    val renderMaxWidth = remember { (displayMetrics.widthPixels * 1.5f).toInt().coerceIn(1080, 2160) }
    val renderMaxHeight = remember { (displayMetrics.heightPixels * 1.5f).toInt().coerceIn(1920, 3840) }
    val coroutineScope = rememberCoroutineScope()

    if (book == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val activeBook = book!!
    val pageCount = activeBook.pageCount

    // Keep screen on behavior based on settings
    val activity = context as? Activity
    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Set brightness local value override
    LaunchedEffect(readerBrightness) {
        activity?.let { act ->
            val layoutParams = act.window.attributes
            layoutParams.screenBrightness = if (readerBrightness < 0) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                readerBrightness.coerceIn(0.01f, 1.0f)
            }
            act.window.attributes = layoutParams
        }
    }

    // Set screen orientation lock
    LaunchedEffect(readerOrientation) {
        activity?.let { act ->
            act.requestedOrientation = when (readerOrientation) {
                "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Reset screen behavior when leaving reader
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.let { act ->
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val lp = act.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = lp
            }
        }
    }

    // UI overlays toggle state
    var showOverlays by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showQuickSwitchDrawer by remember { mutableStateOf(false) }

    // Navigation and Page Position States
    var currentPageIndex by remember(activeBook.id) { mutableIntStateOf(activeBook.lastPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }

    val currentBookIndex = allBooks.indexOfFirst { it.id == activeBook.id }
    val hasPrevBook = currentBookIndex > 0
    val hasNextBook = currentBookIndex >= 0 && currentBookIndex < allBooks.size - 1

    fun openPrevBook() {
        if (hasPrevBook) {
            viewModel.openBook(allBooks[currentBookIndex - 1])
        }
    }

    fun openNextBook() {
        if (hasNextBook) {
            viewModel.openBook(allBooks[currentBookIndex + 1])
        }
    }

    // Page state managers based on viewmode
    val pagerState = rememberPagerState(initialPage = currentPageIndex, pageCount = { pageCount })
    val initialScrollOffset = remember(activeBook.id) { activeBook.scrollOffset }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentPageIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset
    )

    // Define saving progress lambda
    val saveProgressLambda = {
        val currentScroll = if (readerMode != "horizontal") {
            listState.firstVisibleItemScrollOffset
        } else {
            0
        }
        viewModel.updateBookProgressDetailed(
            bookId = activeBook.id,
            lastPage = currentPageIndex,
            scrollOffset = currentScroll,
            zoomLevel = 1.0f,
            readingMode = readerMode,
            brightness = readerBrightness,
            orientation = readerOrientation
        )
    }

    // Synchronize page indicators and continuous scroll offset saving
    LaunchedEffect(pagerState.currentPage) {
        if (readerMode == "horizontal") {
            currentPageIndex = pagerState.currentPage
            saveProgressLambda()
        }
    }

    // Keep the page indicator in sync instantly (cheap, local state only).
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (readerMode != "horizontal" && pageCount > 0) {
            currentPageIndex = listState.firstVisibleItemIndex
        }
    }

    // Persist progress (database write + StateFlow update, which triggers
    // recomposition) only after scrolling has paused for a moment, instead of on
    // every single scroll-offset change. The offset changes on nearly every frame
    // during a fling, so doing this expensive work per-frame caused real stutter
    // that felt like the scroll randomly stopping/catching on its own - not
    // something the user's finger was doing.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .debounce(300)
            .collect {
                if (readerMode != "horizontal" && pageCount > 0) {
                    saveProgressLambda()
                }
            }
    }

    // Automatically save progress when backgrounding/minimizing/disposing
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeBook.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                saveProgressLambda()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            saveProgressLambda()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Scroll page programmatically
    fun jumpToPage(index: Int) {
        val target = index.coerceIn(0, pageCount - 1)
        currentPageIndex = target
        coroutineScope.launch {
            if (readerMode == "horizontal") {
                pagerState.scrollToPage(target)
            } else {
                listState.scrollToItem(target, 0)
            }
            saveProgressLambda()
        }
    }

    // Collect volume key turns
    LaunchedEffect(Unit) {
        viewModel.pageTurnEvent.collect { isForward ->
            if (isForward) {
                if (currentPageIndex < pageCount - 1) {
                    jumpToPage(currentPageIndex + 1)
                }
            } else {
                if (currentPageIndex > 0) {
                    jumpToPage(currentPageIndex - 1)
                }
            }
        }
    }

    // Handle automated scrolling loop
    LaunchedEffect(isAutoScrolling, autoScrollSpeed, readerMode) {
        if (isAutoScrolling) {
            if (readerMode == "horizontal") {
                while (isAutoScrolling) {
                    val interval = ((21f - autoScrollSpeed.coerceIn(1f, 20f)) * 1000).toLong()
                    kotlinx.coroutines.delay(interval)
                    if (currentPageIndex < pageCount - 1) {
                        jumpToPage(currentPageIndex + 1)
                    } else {
                        isAutoScrolling = false
                    }
                }
            } else {
                while (isAutoScrolling) {
                    val pixelsToScroll = autoScrollSpeed
                    try {
                        listState.scrollBy(pixelsToScroll)
                    } catch (e: Exception) {
                        // user is manually scrolling
                    }
                    kotlinx.coroutines.delay(16)
                }
            }
        }
    }

    // Prefetch upcoming pages so they're already decoded (and cached) by the time they
    // scroll into view. Without this, decoding only started the moment a page entered
    // the viewport, so its container would visibly jump from the placeholder size to
    // the real image size right as the user reached it - feeling like the scroll
    // "cuts" instead of flowing smoothly, unlike Mihon/Google Files.
    LaunchedEffect(currentPageIndex, readerMode) {
        if (readerMode != "horizontal") {
            val prefetchAhead = 3
            for (offset in 1..prefetchAhead) {
                val target = currentPageIndex + offset
                if (target < pageCount) {
                    launch {
                        BookRenderer.getPageBitmap(context, activeBook.id, activeBook.filePath, activeBook.format, target, renderMaxWidth, renderMaxHeight)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("reader_viewport")
    ) {
        // --- 1. CORE PAGES RENDERER (Compose High Performance Canvas & Lists) ---
        if (pageCount <= 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No pages found or corrupted book document",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            if (readerMode == "horizontal") {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 16.dp,
                    userScrollEnabled = true
                ) { pageIdx ->
                    ReaderPageItem(
                        context = context,
                        book = activeBook,
                        pageIndex = pageIdx,
                        readerMode = readerMode,
                        comfortFilter = readerComfortFilter,
                        renderMaxWidth = renderMaxWidth,
                        renderMaxHeight = renderMaxHeight,
                        onCenterTap = { showOverlays = !showOverlays },
                        onEdgeTap = { isForward ->
                            if (isForward) {
                                if (currentPageIndex < pageCount - 1) jumpToPage(currentPageIndex + 1)
                            } else {
                                if (currentPageIndex > 0) jumpToPage(currentPageIndex - 1)
                            }
                        }
                    )
                }
            } else {
                // Vertical Continuous or Webtoon mode
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageCount) { pageIdx ->
                        ReaderPageItem(
                            context = context,
                            book = activeBook,
                            pageIndex = pageIdx,
                            readerMode = readerMode,
                            comfortFilter = readerComfortFilter,
                            renderMaxWidth = renderMaxWidth,
                            renderMaxHeight = renderMaxHeight,
                            onCenterTap = { showOverlays = !showOverlays },
                            onEdgeTap = { isForward ->
                                if (isForward) {
                                    if (currentPageIndex < pageCount - 1) jumpToPage(currentPageIndex + 1)
                                } else {
                                    if (currentPageIndex > 0) jumpToPage(currentPageIndex - 1)
                                }
                            }
                        )
                        if (readerMode == "vertical") {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(Color.DarkGray)
                            )
                        }
                    }
                }
            }
        }

        // --- 2. TOP & BOTTOM OVERLAY CONTROLS ---
        AnimatedVisibility(
            visible = showOverlays,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically()
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = activeBook.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeBook(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Playlist/Folder Library Toggle
                    IconButton(onClick = { showQuickSwitchDrawer = true }) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = "Show Folder Playlist",
                            tint = Color.White
                        )
                    }
                    // Bookmark toggle
                    val hasBookmark = bookmarks.any { it.page == currentPageIndex }
                    IconButton(onClick = { viewModel.toggleBookmarkAtPage(currentPageIndex) }) {
                        Icon(
                            imageVector = if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark Page",
                            tint = if (hasBookmark) Color.Yellow else Color.White
                        )
                    }
                    // Settings Toggle
                    IconButton(onClick = { showSettingsPanel = true }) {
                        Icon(Icons.Default.Tune, "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        AnimatedVisibility(
            visible = showOverlays,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slider and progress metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentPageIndex + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Start
                    )

                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { jumpToPage(it.roundToInt()) },
                        valueRange = 0f..((pageCount - 1).coerceAtLeast(0).toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$pageCount",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val progressPercentage = if (pageCount > 0) {
                    ((currentPageIndex + 1).toFloat() / pageCount * 100).toInt()
                } else 0

                Text(
                    text = "Progress: $progressPercentage% • Page ${currentPageIndex + 1} of $pageCount",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Media-player-like Book skip playlist controllers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { openPrevBook() },
                        enabled = hasPrevBook,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Book",
                            tint = if (hasPrevBook) Color.White else Color.DarkGray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Button(
                        onClick = { showQuickSwitchDrawer = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "Library Playlist",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Library Playlist",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    IconButton(
                        onClick = { openNextBook() },
                        enabled = hasNextBook,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Book",
                            tint = if (hasNextBook) Color.White else Color.DarkGray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // --- 3. QUICK SWITCH FAB ---
        AnimatedVisibility(
            visible = !showOverlays,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = { showQuickSwitchDrawer = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.testTag("quick_switch_fab")
            ) {
                Icon(Icons.Default.SwapHoriz, "Quick Switch Books")
            }
        }

        // --- 4. CUSTOM QUICK SWITCH DRAWER (Side Drawer Overlay) ---
        AnimatedVisibility(
            visible = showQuickSwitchDrawer,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {} // block click propagation
                    .systemBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library Playlist",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { showQuickSwitchDrawer = false }) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allBooks) { b ->
                            Card(
                                onClick = {
                                    viewModel.openBook(b)
                                    showQuickSwitchDrawer = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (b.id == activeBook.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (b.format == "pdf") Icons.Default.Description else Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = if (b.format == "pdf") Color(0xFFE57373) else Color(0xFF64B5F6),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = b.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. READER SETTINGS BOTTOM SHEET PANEL ---
        if (showSettingsPanel) {
            AlertDialog(
                onDismissRequest = { showSettingsPanel = false },
                title = { Text("Reader Configuration") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Reading Mode Selector
                        Column {
                            Text("Reading Layout", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setReaderMode("horizontal") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (readerMode == "horizontal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (readerMode == "horizontal") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Pager")
                                }
                                Button(
                                    onClick = { viewModel.setReaderMode("vertical") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (readerMode == "vertical") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (readerMode == "vertical") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Vertical")
                                }
                                Button(
                                    onClick = { viewModel.setReaderMode("webtoon") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (readerMode == "webtoon") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (readerMode == "webtoon") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Webtoon")
                                }
                            }
                        }

                        // 2. Local Brightness Adjustment
                        Column {
                            Text("Reader Brightness", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.LightMode, "Brightness")
                                Slider(
                                    value = if (readerBrightness < 0) 0.5f else readerBrightness,
                                    onValueChange = { viewModel.setReaderBrightness(it) },
                                    valueRange = 0.05f..1.0f,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.setReaderBrightness(-1f) }) {
                                    Text(if (readerBrightness < 0) "Auto" else "Reset")
                                }
                            }
                        }

                        // Eye Comfort Filter selector
                        Column {
                            Text("Eye Comfort Filter", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { readerComfortFilter = "none" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (readerComfortFilter == "none") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (readerComfortFilter == "none") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("None")
                                }
                                Button(
                                    onClick = { readerComfortFilter = "sepia" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (readerComfortFilter == "sepia") Color(0xFFF4ECD8) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (readerComfortFilter == "sepia") Color(0xFF5D4037) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sepia")
                                }
                                Button(
                                    onClick = { readerComfortFilter = "invert" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (readerComfortFilter == "invert") Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (readerComfortFilter == "invert") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Invert")
                                }
                            }
                        }

                        // 3. Keep Screen On Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Keep Screen On", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { viewModel.setKeepScreenOn(it) }
                            )
                        }

                        // 4. Orientation Lock Selector
                        Column {
                            Text("Screen Rotation", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = readerOrientation == "sensor",
                                    onClick = { viewModel.setReaderOrientation("sensor") },
                                    label = { Text("Auto Rotation") }
                                )
                                FilterChip(
                                    selected = readerOrientation == "portrait",
                                    onClick = { viewModel.setReaderOrientation("portrait") },
                                    label = { Text("Portrait") }
                                )
                                FilterChip(
                                    selected = readerOrientation == "landscape",
                                    onClick = { viewModel.setReaderOrientation("landscape") },
                                    label = { Text("Landscape") }
                                )
                            }
                        }

                        HorizontalDivider()

                        // 5. Auto Scroll Controls
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto Scroll", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                IconButton(onClick = { isAutoScrolling = !isAutoScrolling }) {
                                    Icon(
                                        imageVector = if (isAutoScrolling) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = "Toggle Auto Scroll",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Speed, null, modifier = Modifier.size(20.dp))
                                Slider(
                                    value = autoScrollSpeed,
                                    onValueChange = { autoScrollSpeed = it },
                                    valueRange = 1f..30f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${autoScrollSpeed.roundToInt()}/30", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        HorizontalDivider()

                        // 6. Manual Save Page & Saved Pages List
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Saved Pages", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Button(
                                    onClick = { viewModel.savePageManually(currentPageIndex) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Page", fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val savedPages = bookmarks.filter { it.isSavedPage }
                            if (savedPages.isEmpty()) {
                                Text("No manually saved pages yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    savedPages.forEach { sp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .clickable { jumpToPage(sp.page); showSettingsPanel = false }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(sp.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            }
                                            IconButton(onClick = { viewModel.deleteBookmark(sp.id) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // 7. Bookmarks Organization & List
                        Column {
                            Text("Bookmarks", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            val normalBookmarks = bookmarks.filter { !it.isSavedPage }
                            if (normalBookmarks.isEmpty()) {
                                Text("No bookmarks added yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    normalBookmarks.forEach { bm ->
                                        var showRenameDialog by remember { mutableStateOf(false) }
                                        var tempLabel by remember { mutableStateOf(bm.label) }
                                        if (showRenameDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showRenameDialog = false },
                                                title = { Text("Rename Bookmark") },
                                                text = {
                                                    OutlinedTextField(
                                                        value = tempLabel,
                                                        onValueChange = { tempLabel = it },
                                                        singleLine = true,
                                                        label = { Text("Bookmark Label") }
                                                    )
                                                },
                                                confirmButton = {
                                                    Button(onClick = {
                                                        viewModel.renameBookmark(bm.id, tempLabel)
                                                        showRenameDialog = false
                                                    }) {
                                                        Text("Rename")
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showRenameDialog = false }) {
                                                        Text("Cancel")
                                                    }
                                                }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .clickable { jumpToPage(bm.page); showSettingsPanel = false }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(bm.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Row {
                                                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                }
                                                IconButton(onClick = { viewModel.deleteBookmark(bm.id) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSettingsPanel = false }) {
                        Text("Apply")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderPageItem(
    context: Context,
    book: BookEntity,
    pageIndex: Int,
    readerMode: String,
    comfortFilter: String,
    renderMaxWidth: Int,
    renderMaxHeight: Int,
    onCenterTap: () -> Unit,
    onEdgeTap: (Boolean) -> Unit
) {
    var bitmap by remember(book.id, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(book.id, pageIndex) { mutableStateOf(true) }
    // Seed from the synchronous cache so an already-prefetched page is sized
    // correctly on its very first composition, instead of starting from a
    // placeholder ratio and visibly jumping once the async load resolves.
    var aspectRatio by remember(book.id, pageIndex) {
        mutableFloatStateOf(BookRenderer.getCachedAspectRatio(book.id, pageIndex) ?: 0.7f)
    }

    // Async loader
    LaunchedEffect(book.id, pageIndex) {
        isLoading = true
        val bmp = BookRenderer.getPageBitmap(context, book.id, book.filePath, book.format, pageIndex, renderMaxWidth, renderMaxHeight)
        bitmap = bmp
        if (bmp != null && bmp.height > 0) {
            aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
        }
        isLoading = false
    }

    // Split extremely long vertical strips into smaller sequential chunks (max height 2048px)
    val maxChunkHeight = 2048
    val slices = remember(bitmap) {
        val bmp = bitmap
        if (bmp == null) {
            emptyList()
        } else if (bmp.height <= maxChunkHeight) {
            listOf(bmp)
        } else {
            val list = mutableListOf<Bitmap>()
            val w = bmp.width
            val h = bmp.height
            var startY = 0
            while (startY < h) {
                val chunkH = minOf(maxChunkHeight, h - startY)
                try {
                    val chunk = Bitmap.createBitmap(bmp, 0, startY, w, chunkH)
                    list.add(chunk)
                } catch (e: Exception) {
                    android.util.Log.e("ReaderPageItem", "Failed to create slice at startY $startY, height $chunkH", e)
                }
                startY += chunkH
            }
            list
        }
    }

    // aspectRatio is now managed directly as state above (seeded from the sync cache
    // and updated once the real bitmap loads), so no separate derived value is needed here.

    val containerModifier = when {
        readerMode == "horizontal" -> Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .aspectRatio(aspectRatio)
        // When a page is split into multiple slices, don't ALSO force the container's
        // height from the full-page aspect ratio. Each slice already sizes itself from
        // its own real aspect ratio; letting both a top-down (full-page ratio) and a
        // bottom-up (summed slice heights) constraint apply at once let them drift out
        // of sync (rounding across many slices), causing panels to overlap the next
        // page or leave a gap - now content height is the single source of truth.
        slices.size > 1 -> Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        else -> Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    }

    // Interactive zoom and pan modifiers
    var scale by remember(book.id, pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(book.id, pageIndex) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    BoxWithConstraints(
        modifier = containerModifier
            .background(Color.Black)
            .combinedClickable(
                onClick = {}, // consumes standard simple clicks to avoid list trigger
                onDoubleClick = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                }
            )
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = transformState)
                // Custom pointer input to separate edge taps vs center tap menu toggle
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val screenWidth = size.width
                            val edgeThreshold = screenWidth * 0.20f // 20% on edges is tap navigation zones
                            if (tapOffset.x < edgeThreshold) {
                                onEdgeTap(false) // Left tap -> backward
                            } else if (tapOffset.x > screenWidth - edgeThreshold) {
                                onEdgeTap(true) // Right tap -> forward
                            } else {
                                onCenterTap() // Center tap -> toggle overlay menus
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading Page ${pageIndex + 1}...", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                bitmap?.let { bmp ->
                    val colorFilter = when (comfortFilter) {
                        "invert" -> {
                            val matrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
                                 0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
                                 0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
                                 0.0f,  0.0f,  0.0f, 1.0f,   0.0f
                            ))
                            androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix)
                        }
                        "sepia" -> {
                            androidx.compose.ui.graphics.ColorFilter.tint(
                                Color(0xFFF2EAD4).copy(alpha = 0.25f),
                                androidx.compose.ui.graphics.BlendMode.Multiply
                            )
                        }
                        else -> null
                    }

                    if (slices.size > 1) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            slices.forEach { sliceBmp ->
                                val sliceRatio = sliceBmp.width.toFloat() / sliceBmp.height.toFloat()
                                Image(
                                    bitmap = sliceBmp.asImageBitmap(),
                                    contentDescription = "Page ${pageIndex + 1} Slice",
                                    contentScale = ContentScale.FillWidth,
                                    colorFilter = colorFilter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(sliceRatio)
                                )
                            }
                        }
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } ?: Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Failed to render page ${pageIndex + 1}", color = Color.White)
                }
            }
        }
    }
}
