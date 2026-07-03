package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.BookViewModel
import com.example.util.BookRenderer
import com.example.util.Localization
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BookViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val darkModeSetting by viewModel.darkModeSetting.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

    // Personalization Settings
    val accentColorSetting by viewModel.accentColorSetting.collectAsState()
    val materialYouSetting by viewModel.materialYouSetting.collectAsState()
    val cornerStyleSetting by viewModel.cornerStyleSetting.collectAsState()
    val uiFontSizeSetting by viewModel.uiFontSizeSetting.collectAsState()
    val readerFontSizeSetting by viewModel.readerFontSizeSetting.collectAsState()
    val iconsStyleSetting by viewModel.iconsStyleSetting.collectAsState()
    val layoutSpacingSetting by viewModel.layoutSpacingSetting.collectAsState()
    val navigationStyleSetting by viewModel.navigationStyleSetting.collectAsState()
    val animationsLevelSetting by viewModel.animationsLevelSetting.collectAsState()
    val appLanguageSetting by viewModel.appLanguageSetting.collectAsState()
    val volumeKeysPageTurn by viewModel.volumeKeysPageTurn.collectAsState()
    val foldersList by viewModel.foldersList.collectAsState()

    // Localized strings helper
    fun getString(key: String): String = Localization.getString(appLanguageSetting, key)

    var showResetDialog by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                val displayName = viewModel.getPathDisplayName(it.toString())
                viewModel.addFolder(it.toString(), displayName)
            } catch (e: Exception) {
                val displayName = viewModel.getPathDisplayName(it.toString())
                viewModel.addFolder(it.toString(), displayName)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getString("settings"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Storage & Directories
            SettingsHeader(getString("manage_folders"))
            SettingsCard {
                Column {
                    foldersList.forEachIndexed { index, folder ->
                        val isActive = viewModel.activeFolderUri.collectAsState().value == folder.uri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder.alias,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = folder.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${folder.pdfCount} PDFs, ${folder.cbzCount} CBZs • " +
                                            if (folder.lastScanTime > 0) {
                                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(folder.lastScanTime))
                                            } else "Never scanned",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rename Alias button
                                var showRenameDialog by remember { mutableStateOf(false) }
                                var tempAlias by remember { mutableStateOf(folder.alias) }
                                if (showRenameDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showRenameDialog = false },
                                        title = { Text(getString("rename_alias")) },
                                        text = {
                                            OutlinedTextField(
                                                value = tempAlias,
                                                onValueChange = { tempAlias = it },
                                                singleLine = true,
                                                label = { Text("Alias") }
                                            )
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                viewModel.renameFolderAlias(folder.uri, tempAlias)
                                                showRenameDialog = false
                                            }) {
                                                Text("OK")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showRenameDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }
                                
                                IconButton(onClick = { showRenameDialog = true }) {
                                    Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }

                                // Switch folder
                                IconButton(onClick = { viewModel.setActiveFolder(folder.uri) }) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Activate",
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Remove Folder
                                IconButton(onClick = { viewModel.removeFolder(folder.uri) }) {
                                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (index < foldersList.size - 1) {
                            HorizontalDivider()
                        }
                    }

                    Button(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getString("add_folder"))
                    }
                }
            }

            // Section 2: Appearance & Theme Customization
            SettingsHeader(getString("appearance_themes"))
            SettingsCard {
                Column {
                    // Application Theme Select
                    var showThemeDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.Palette,
                        title = getString("app_theme"),
                        subtitle = when (darkModeSetting) {
                            "light" -> getString("light")
                            "dark" -> getString("dark")
                            "amoled" -> getString("amoled")
                            "dynamic" -> getString("material_you")
                            else -> getString("dark")
                        },
                        onClick = { showThemeDialog = true }
                    )

                    SelectionDialog(
                        show = showThemeDialog,
                        onDismiss = { showThemeDialog = false },
                        title = getString("app_theme"),
                        options = listOf("light", "dark", "amoled", "dynamic", "system"),
                        selectedOption = darkModeSetting,
                        onOptionSelected = { viewModel.setDarkMode(it) },
                        optionLabel = {
                            when (it) {
                                "light" -> getString("light")
                                "dark" -> getString("dark")
                                "amoled" -> getString("amoled")
                                "dynamic" -> getString("material_you")
                                else -> "System Default"
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Accent Colors Selector
                    var showAccentDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.Brush,
                        title = getString("accent_color"),
                        subtitle = accentColorSetting,
                        onClick = { showAccentDialog = true }
                    )

                    SelectionDialog(
                        show = showAccentDialog,
                        onDismiss = { showAccentDialog = false },
                        title = getString("accent_color"),
                        options = listOf("Blue", "Red", "Green", "Purple", "Orange", "Yellow", "Pink", "Cyan", "Teal", "Indigo"),
                        selectedOption = accentColorSetting,
                        onOptionSelected = { viewModel.setAccentColor(it) },
                        optionLabel = { it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Material You toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ColorLens, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(getString("material_you"), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text("Adapts scheme to wallpaper colors (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = materialYouSetting, onCheckedChange = { viewModel.setMaterialYou(it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Corner style
                    var showCornerDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.Category,
                        title = getString("corner_style"),
                        subtitle = cornerStyleSetting.uppercase(),
                        onClick = { showCornerDialog = true }
                    )

                    SelectionDialog(
                        show = showCornerDialog,
                        onDismiss = { showCornerDialog = false },
                        title = getString("corner_style"),
                        options = listOf("rounded", "medium", "square"),
                        selectedOption = cornerStyleSetting,
                        onOptionSelected = { viewModel.setCornerStyle(it) },
                        optionLabel = { it.uppercase() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // UI Font Size
                    var showUiFontDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.FormatSize,
                        title = getString("ui_font_size"),
                        subtitle = uiFontSizeSetting.uppercase(),
                        onClick = { showUiFontDialog = true }
                    )

                    SelectionDialog(
                        show = showUiFontDialog,
                        onDismiss = { showUiFontDialog = false },
                        title = getString("ui_font_size"),
                        options = listOf("small", "default", "large", "extra_large"),
                        selectedOption = uiFontSizeSetting,
                        onOptionSelected = { viewModel.setUiFontSize(it) },
                        optionLabel = { it.uppercase().replace("_", " ") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Icons Style
                    var showIconsDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.InsertEmoticon,
                        title = getString("icons_style"),
                        subtitle = iconsStyleSetting.uppercase(),
                        onClick = { showIconsDialog = true }
                    )

                    SelectionDialog(
                        show = showIconsDialog,
                        onDismiss = { showIconsDialog = false },
                        title = getString("icons_style"),
                        options = listOf("filled", "outlined", "rounded"),
                        selectedOption = iconsStyleSetting,
                        onOptionSelected = { viewModel.setIconsStyle(it) },
                        optionLabel = { it.uppercase() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Spacing style
                    var showSpacingDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.ViewHeadline,
                        title = getString("icons_style"),
                        subtitle = layoutSpacingSetting.uppercase(),
                        onClick = { showSpacingDialog = true }
                    )

                    SelectionDialog(
                        show = showSpacingDialog,
                        onDismiss = { showSpacingDialog = false },
                        title = getString("icons_style"),
                        options = listOf("compact", "comfortable", "large"),
                        selectedOption = layoutSpacingSetting,
                        onOptionSelected = { viewModel.setLayoutSpacing(it) },
                        optionLabel = { it.uppercase() }
                    )
                }
            }

            // Section 3: Extra Operational Preferences
            SettingsCard {
                Column {
                    // Navigation Style Option
                    var showNavDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.Navigation,
                        title = getString("navigation_style"),
                        subtitle = navigationStyleSetting.uppercase(),
                        onClick = { showNavDialog = true }
                    )

                    SelectionDialog(
                        show = showNavDialog,
                        onDismiss = { showNavDialog = false },
                        title = getString("navigation_style"),
                        options = listOf("bottom_nav", "drawer", "rail"),
                        selectedOption = navigationStyleSetting,
                        onOptionSelected = { viewModel.setNavigationStyle(it) },
                        optionLabel = { it.uppercase().replace("_", " ") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Language Switcher Option
                    var showLangDialog by remember { mutableStateOf(false) }
                    SettingsRowItem(
                        icon = Icons.Default.Language,
                        title = getString("language"),
                        subtitle = appLanguageSetting.uppercase(),
                        onClick = { showLangDialog = true }
                    )

                    SelectionDialog(
                        show = showLangDialog,
                        onDismiss = { showLangDialog = false },
                        title = getString("language"),
                        options = listOf("en", "es", "fr", "ja", "zh"),
                        selectedOption = appLanguageSetting,
                        onOptionSelected = { viewModel.setAppLanguage(it) },
                        optionLabel = {
                            when (it) {
                                "en" -> "English"
                                "es" -> "Español"
                                "fr" -> "Français"
                                "ja" -> "日本語"
                                "zh" -> "中文"
                                else -> "English"
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Volume key turning
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(getString("volume_keys"), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text("Turns pages using physical volume buttons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = volumeKeysPageTurn, onCheckedChange = { viewModel.setVolumeKeysPageTurn(it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Keep Screen On Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ScreenLockRotation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(getString("keep_screen_on"), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text("Prevents screen timeout during reading", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { viewModel.setKeepScreenOn(it) }
                        )
                    }
                }
            }

            // Section 4: Backup & Restore Configuration
            SettingsHeader(getString("backup_restore"))
            SettingsCard {
                Column {
                    SettingsRowItem(
                        icon = Icons.Default.CloudUpload,
                        title = "Export Preferences configuration",
                        subtitle = "Copy active themes, colors & sizes JSON to clipboard",
                        onClick = {
                            val json = viewModel.exportSettingsToJson()
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("OfflineReaderSettings", json)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Settings JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    var showImportDialog by remember { mutableStateOf(false) }
                    var importText by remember { mutableStateOf("") }
                    if (showImportDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportDialog = false },
                            title = { Text("Import Configuration") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Paste preferences JSON text below:", style = MaterialTheme.typography.bodyMedium)
                                    OutlinedTextField(
                                        value = importText,
                                        onValueChange = { importText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp),
                                        placeholder = { Text("{ ... }") }
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val ok = viewModel.importSettingsFromJson(importText)
                                    if (ok) {
                                        Toast.makeText(context, "Configuration restored successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Invalid JSON configuration", Toast.LENGTH_SHORT).show()
                                    }
                                    showImportDialog = false
                                }) {
                                    Text("Restore")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    SettingsRowItem(
                        icon = Icons.Default.CloudDownload,
                        title = "Import Preferences configuration",
                        subtitle = "Paste themes & settings JSON to restore",
                        onClick = { showImportDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    SettingsRowItem(
                        icon = Icons.Default.Undo,
                        title = getString("reset_appearance"),
                        subtitle = "Resets colors, shapes, and margins to factory Teal",
                        onClick = {
                            viewModel.resetAppearanceSettings()
                            Toast.makeText(context, "Appearance reset successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    SettingsRowItem(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = getString("restore_defaults"),
                        subtitle = "Resets all app settings, languages and volume hotkeys",
                        onClick = {
                            viewModel.restoreDefaultSettings()
                            Toast.makeText(context, "Defaults restored successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Section 5: Performance & Cache Utilities
            SettingsHeader("Performance")
            SettingsCard {
                Column {
                    SettingsRowItem(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Image & Render Cache",
                        subtitle = "Frees up active memory and cached page drawings",
                        onClick = {
                            BookRenderer.clearCache()
                            Toast.makeText(context, "Render cache cleared successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Section 6: Maintenance & Resets (Danger Zone)
            SettingsHeader("Danger Zone")
            SettingsCard(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)) {
                SettingsRowItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Complete App Reset",
                    subtitle = "Wipes database catalog, reading histories, and configurations",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showResetDialog = true }
                )
            }

            // Section 7: About details
            SettingsHeader("About Offline Reader")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Offline Reader v2.0.0",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A premium offline ebook, comic and document reader equipped with dynamic in-app language switching, advanced bookmarks, multiple folders mapping, and a custom rendering engine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All App Data?") },
            text = { Text("This will permanently clear your reading histories, favorites, bookmarks, and folder paths. Physical books on disk will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.resetAllData()
                            showResetDialog = false
                            onBack() // pop back to welcome
                        }
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
        content = { content() }
    )
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun <T> SelectionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (option == selectedOption) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onOptionSelected(option); onDismiss() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == selectedOption, onClick = { onOptionSelected(option); onDismiss() })
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
