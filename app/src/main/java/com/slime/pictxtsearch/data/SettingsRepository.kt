package com.slime.pictxtsearch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slime.pictxtsearch.ui.theme.DEFAULT_HIGHLIGHT_ALPHA
import com.slime.pictxtsearch.ui.theme.DEFAULT_HIGHLIGHT_COLOR
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

enum class AppLanguage {
    FOLLOW_SYSTEM,
    CHINESE,
    ENGLISH,
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val themeSeedIndex: Int = 0,
    val highlightColor: Int = DEFAULT_HIGHLIGHT_COLOR,
    val highlightAlpha: Float = DEFAULT_HIGHLIGHT_ALPHA,
    val language: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.name)
                }.getOrDefault(ThemeMode.FOLLOW_SYSTEM),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
                themeSeedIndex = (prefs[KEY_THEME_SEED] ?: 0).coerceAtLeast(0),
                highlightColor = prefs[KEY_HIGHLIGHT_COLOR] ?: DEFAULT_HIGHLIGHT_COLOR,
                highlightAlpha = (prefs[KEY_HIGHLIGHT_ALPHA] ?: DEFAULT_HIGHLIGHT_ALPHA)
                    .coerceIn(MIN_ALPHA, MAX_ALPHA),
                language = runCatching {
                    AppLanguage.valueOf(prefs[KEY_LANGUAGE] ?: AppLanguage.FOLLOW_SYSTEM.name)
                }.getOrDefault(AppLanguage.FOLLOW_SYSTEM),
            )
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[KEY_ONBOARDING] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeSeedIndex(index: Int) {
        context.settingsDataStore.edit { it[KEY_THEME_SEED] = index }
    }

    suspend fun setHighlightColor(color: Int) {
        context.settingsDataStore.edit { it[KEY_HIGHLIGHT_COLOR] = color }
    }

    suspend fun setHighlightAlpha(alpha: Float) {
        context.settingsDataStore.edit { it[KEY_HIGHLIGHT_ALPHA] = alpha.coerceIn(MIN_ALPHA, MAX_ALPHA) }
    }

    suspend fun setLanguage(language: AppLanguage) {
        AppLanguageCache.write(context, language)
        context.settingsDataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    suspend fun resetToDefaults() {
        AppLanguageCache.write(context, AppLanguage.FOLLOW_SYSTEM)
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = ThemeMode.FOLLOW_SYSTEM.name
            prefs[KEY_DYNAMIC_COLOR] = true
            prefs[KEY_THEME_SEED] = 0
            prefs[KEY_HIGHLIGHT_COLOR] = DEFAULT_HIGHLIGHT_COLOR
            prefs[KEY_HIGHLIGHT_ALPHA] = DEFAULT_HIGHLIGHT_ALPHA
            prefs[KEY_LANGUAGE] = AppLanguage.FOLLOW_SYSTEM.name
        }
    }

    private companion object {
        const val MIN_ALPHA = 0.1f
        const val MAX_ALPHA = 0.8f

        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_THEME_SEED = intPreferencesKey("theme_seed_index")
        val KEY_HIGHLIGHT_COLOR = intPreferencesKey("highlight_color")
        val KEY_HIGHLIGHT_ALPHA = floatPreferencesKey("highlight_alpha")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
    }
}
