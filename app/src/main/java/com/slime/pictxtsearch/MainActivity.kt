package com.slime.pictxtsearch

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slime.pictxtsearch.data.AppLanguage
import com.slime.pictxtsearch.data.AppLanguageCache
import com.slime.pictxtsearch.data.ThemeMode
import com.slime.pictxtsearch.ui.AppViewModel
import com.slime.pictxtsearch.ui.PicTxtSearchApp
import com.slime.pictxtsearch.ui.theme.PicTxtSearchTheme
import com.slime.pictxtsearch.util.withAppLanguage

class MainActivity : ComponentActivity() {

    private var appliedLanguage: AppLanguage = AppLanguage.FOLLOW_SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppLanguageCache.read(newBase)
        super.attachBaseContext(newBase.withAppLanguage(appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(state.settingsLoaded, settings.language) {
                if (state.settingsLoaded && settings.language != appliedLanguage) {
                    appliedLanguage = settings.language
                    recreate()
                }
            }

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                val window = this@MainActivity.window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity,
            ) {
                PicTxtSearchTheme(
                    darkTheme = darkTheme,
                    dynamicColor = settings.dynamicColor,
                    themeSeedIndex = settings.themeSeedIndex,
                ) {
                    PicTxtSearchApp(
                        state = state,
                        settings = settings,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}
