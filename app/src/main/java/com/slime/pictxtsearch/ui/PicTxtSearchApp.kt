package com.slime.pictxtsearch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slime.pictxtsearch.BuildConfig
import com.slime.pictxtsearch.data.AppSettings
import com.slime.pictxtsearch.ui.screens.AboutScreen
import com.slime.pictxtsearch.ui.screens.AuthorScreen
import com.slime.pictxtsearch.ui.screens.CameraScreen
import com.slime.pictxtsearch.ui.screens.HomeScreen
import com.slime.pictxtsearch.ui.screens.OnboardingScreen
import com.slime.pictxtsearch.ui.screens.SettingsScreen

@Composable
fun PicTxtSearchApp(
    state: AppUiState,
    settings: AppSettings,
    viewModel: AppViewModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(
        enabled = state.screen == AppScreen.Home && state.image != null,
    ) {
        viewModel.clearImage()
    }

    BackHandler(
        enabled = state.screen == AppScreen.Camera ||
            state.screen == AppScreen.Settings ||
            state.screen == AppScreen.About ||
            state.screen == AppScreen.Author,
    ) {
        viewModel.navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.settingsLoaded) {
            AnimatedContent(
                targetState = state.screen,
                transitionSpec = {
                    (
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                initialOffsetX = { it / 18 },
                            )
                        ).togetherWith(
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                    )
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    AppScreen.Onboarding -> OnboardingScreen(
                        onFinished = viewModel::completeOnboarding,
                    )

                    AppScreen.Home -> HomeScreen(
                        state = state,
                        settings = settings,
                        onOpenCamera = viewModel::openCamera,
                        onOpenSettings = viewModel::openSettings,
                        onImageSelected = { uri -> viewModel.onImageSelected(uri) },
                        onQueryChange = viewModel::updateQuery,
                        onToggleRegex = viewModel::setRegexEnabled,
                        onToggleCaseSensitive = viewModel::setCaseSensitive,
                        onTogglePlainText = viewModel::setPlainTextEnabled,
                        onExport = viewModel::exportHighlightedImage,
                        onClearImage = viewModel::clearImage,
                        onMessage = viewModel::showMessage,
                        onOpenHistoryRecord = viewModel::openHistoryRecord,
                        onDeleteHistoryRecords = viewModel::deleteHistoryRecords,
                        onRenameHistoryRecord = viewModel::renameHistoryRecord,
                        onPreviousMatch = viewModel::previousMatch,
                        onNextMatch = viewModel::nextMatch,
                    )

                    AppScreen.Camera -> CameraScreen(
                        onBack = viewModel::navigateBack,
                        onCaptured = { uri ->
                            viewModel.onImageSelected(uri, deleteSourceAfterLoad = true)
                        },
                        onImagePicked = { uri -> viewModel.onImageSelected(uri) },
                    )

                    AppScreen.Settings -> SettingsScreen(
                        settings = settings,
                        onBack = viewModel::navigateBack,
                        onOpenAbout = viewModel::openAbout,
                        onResetSettings = viewModel::resetSettings,
                        onThemeModeChange = viewModel::setThemeMode,
                        onDynamicColorChange = viewModel::setDynamicColor,
                        onThemeSeedSelected = viewModel::setThemeSeedIndex,
                        onHighlightColorSelected = viewModel::setHighlightColor,
                        onHighlightAlphaChange = viewModel::setHighlightAlpha,
                        onLanguageChange = viewModel::setLanguage,
                    )

                    AppScreen.About -> AboutScreen(
                        versionName = BuildConfig.VERSION_NAME,
                        onBack = viewModel::navigateBack,
                        onOpenAuthor = viewModel::openAuthor,
                        onMessage = viewModel::showMessage,
                    )

                    AppScreen.Author -> AuthorScreen(
                        onBack = viewModel::navigateBack,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}
