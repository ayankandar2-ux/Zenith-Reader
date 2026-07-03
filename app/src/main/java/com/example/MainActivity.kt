package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BookViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkModeSetting by viewModel.darkModeSetting.collectAsState()
            val accentColorSetting by viewModel.accentColorSetting.collectAsState()
            val materialYouSetting by viewModel.materialYouSetting.collectAsState()
            val cornerStyleSetting by viewModel.cornerStyleSetting.collectAsState()
            val uiFontSizeSetting by viewModel.uiFontSizeSetting.collectAsState()

            MyApplicationTheme(
                themeSetting = darkModeSetting,
                accentColor = accentColorSetting,
                materialYou = materialYouSetting,
                cornerStyle = cornerStyleSetting,
                uiFontSize = uiFontSizeSetting
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
                    val currentBook by viewModel.currentBook.collectAsState()
                    var currentScreen by remember { mutableStateOf("library") }

                    when {
                        selectedFolderUri.isEmpty() -> {
                            WelcomeScreen(onFolderSelected = { viewModel.setFolderUri(it) })
                        }
                        currentBook != null -> {
                            ReaderScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.closeBook() }
                            )
                        }
                        else -> {
                            when (currentScreen) {
                                "library" -> LibraryScreen(
                                    viewModel = viewModel,
                                    onBookSelected = { viewModel.openBook(it) },
                                    onNavigateToSettings = { currentScreen = "settings" }
                               )
                                "settings" -> SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = "library" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val volumeKeysEnabled = viewModel.volumeKeysPageTurn.value
        if (volumeKeysEnabled && viewModel.currentBook.value != null) {
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                viewModel.turnPage(isForward = false)
                return true
            } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                viewModel.turnPage(isForward = true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
