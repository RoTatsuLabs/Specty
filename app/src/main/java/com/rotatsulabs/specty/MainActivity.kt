package com.rotatsulabs.specty

import com.rotatsulabs.specty.dev.AppLog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rotatsulabs.specty.ui.AppLanguage
import com.rotatsulabs.specty.ui.MainViewModel
import com.rotatsulabs.specty.ui.SpectyApp
import androidx.compose.runtime.CompositionLocalProvider
import com.rotatsulabs.specty.ui.design.LocalThemeReveal
import com.rotatsulabs.specty.ui.design.ThemeRevealAccent
import com.rotatsulabs.specty.ui.design.ThemeRevealHost
import com.rotatsulabs.specty.ui.design.rememberThemeReveal
import com.rotatsulabs.specty.ui.theme.SpectyTheme
import com.rotatsulabs.specty.ui.theme.isDarkFor

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.analyze(uri)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addFolder(uri)
    }

    private val excludePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.excludeFolder(uri)
    }

    private val comparePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.startCompare(uri)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(applicationContext)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val dark = isDarkFor(appearance)
            DisposableEffect(dark) {
                // Keep the system bar icons readable against the in-app theme, not just the system one.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val reveal = rememberThemeReveal()
            CompositionLocalProvider(LocalThemeReveal provides reveal) {
                ThemeRevealHost(reveal) {
                    SpectyTheme(appearance) {
                        ThemeRevealAccent(reveal)
                        SpectyApp(
                            viewModel = viewModel,
                            onPickFile = { picker.launch(arrayOf("audio/*", "application/ogg")) },
                            onPickFolder = { folderPicker.launch(null) },
                            onPickExcluded = { excludePicker.launch(null) },
                            onPickCompare = { comparePicker.launch(arrayOf("audio/*", "application/ogg")) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let(viewModel::analyze)
        }
    }
}
