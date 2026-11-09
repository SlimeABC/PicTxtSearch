package com.slime.pictxtsearch.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slime.pictxtsearch.R
import com.slime.pictxtsearch.data.AppLanguage
import com.slime.pictxtsearch.data.AppLanguageCache
import com.slime.pictxtsearch.data.AppSettings
import com.slime.pictxtsearch.data.HistoryItem
import com.slime.pictxtsearch.data.HistoryStore
import com.slime.pictxtsearch.data.SettingsRepository
import com.slime.pictxtsearch.data.ThemeMode
import com.slime.pictxtsearch.model.MatchBox
import com.slime.pictxtsearch.model.OcrResult
import com.slime.pictxtsearch.model.SearchOptions
import com.slime.pictxtsearch.ocr.OcrEngine
import com.slime.pictxtsearch.ocr.OcrMatcher
import com.slime.pictxtsearch.util.ImageExporter
import com.slime.pictxtsearch.util.ImageUtils
import com.slime.pictxtsearch.util.withAppLanguage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    Onboarding,
    Home,
    Camera,
    Settings,
    About,
    Author,
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Home,
    val settingsLoaded: Boolean = false,
    val image: Bitmap? = null,
    val ocrResult: OcrResult? = null,
    val ocrInProgress: Boolean = false,
    val query: String = "",
    val searchOptions: SearchOptions = SearchOptions(),
    val matches: List<MatchBox> = emptyList(),
    val regexError: Boolean = false,
    val exporting: Boolean = false,
    val history: List<HistoryItem> = emptyList(),
    val recordId: String? = null,
    val recordTitle: String = "",
    val matchIndex: Int = -1,
    val matchFocusToken: Int = 0,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val historyStore = HistoryStore(application)

    
    private var ocrEngine: OcrEngine? = null

    private fun obtainOcrEngine(): OcrEngine = ocrEngine ?: OcrEngine().also { ocrEngine = it }

    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = _messages.asSharedFlow()

    private var loadJob: Job? = null
    private var queryPersistJob: Job? = null
    private var imageGeneration = 0

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                AppLanguageCache.write(getApplication(), settings.language)
            }
        }
        viewModelScope.launch {
            val initial = repository.settings.first()
            val history = withContext(Dispatchers.IO) { historyStore.list() }
            _uiState.update {
                it.copy(
                    settingsLoaded = true,
                    screen = if (initial.onboardingCompleted) AppScreen.Home else AppScreen.Onboarding,
                    history = history,
                )
            }
        }
    }

    fun openCamera() {
        _uiState.update { it.copy(screen = AppScreen.Camera) }
    }

    fun openSettings() {
        _uiState.update { it.copy(screen = AppScreen.Settings) }
    }

    fun openAbout() {
        _uiState.update { it.copy(screen = AppScreen.About) }
    }

    fun openAuthor() {
        _uiState.update { it.copy(screen = AppScreen.Author) }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.screen) {
                AppScreen.Author -> state.copy(screen = AppScreen.About)
                AppScreen.About -> state.copy(screen = AppScreen.Settings)
                AppScreen.Camera,
                AppScreen.Settings,
                -> state.copy(screen = AppScreen.Home)
                else -> state
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(true) }
        _uiState.update { it.copy(screen = AppScreen.Home) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setThemeSeedIndex(index: Int) {
        viewModelScope.launch { repository.setThemeSeedIndex(index) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setHighlightColor(color: Int) {
        viewModelScope.launch { repository.setHighlightColor(color) }
    }

    fun setHighlightAlpha(alpha: Float) {
        viewModelScope.launch { repository.setHighlightAlpha(alpha) }
    }

    fun setRegexEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                searchOptions = it.searchOptions.copy(
                    regex = enabled,
                    plainText = if (enabled) false else it.searchOptions.plainText,
                ),
            ).applySearch()
        }
    }

    fun setCaseSensitive(enabled: Boolean) {
        _uiState.update {
            it.copy(searchOptions = it.searchOptions.copy(caseSensitive = enabled)).applySearch()
        }
    }

    fun setPlainTextEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                searchOptions = it.searchOptions.copy(
                    plainText = enabled,
                    regex = if (enabled) false else it.searchOptions.regex,
                ),
            ).applySearch()
        }
    }

    fun showMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun message(id: Int): String {
        val application = getApplication<Application>()
        return application.withAppLanguage(settings.value.language).getString(id)
    }

    
    fun onImageSelected(uri: Uri, deleteSourceAfterLoad: Boolean = false) {
        val requestGeneration = ++imageGeneration
        loadJob?.cancel()
        queryPersistJob?.cancel()
        _uiState.update {
            it.copy(
                screen = AppScreen.Home,
                image = null,
                ocrResult = null,
                ocrInProgress = true,
                query = "",
                matches = emptyList(),
                regexError = false,
                recordId = null,
                recordTitle = "",
                matchIndex = -1,
                matchFocusToken = 0,
            )
        }

        loadJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val decoded = ImageUtils.decodeBitmap(getApplication<Application>(), uri)
                if (deleteSourceAfterLoad) {
                    uri.path?.let { path -> runCatching { File(path).delete() } }
                }
                decoded
            }

            if (requestGeneration != imageGeneration) return@launch
            if (bitmap == null) {
                _uiState.update { it.copy(ocrInProgress = false) }
                _messages.tryEmit(message(R.string.image_read_failed))
                return@launch
            }

            _uiState.update { it.copy(image = bitmap) }
            try {
                val engine = withContext(Dispatchers.Default) { obtainOcrEngine() }
                val result = engine.recognize(bitmap)
                if (requestGeneration != imageGeneration) return@launch
                val savedRecord = withContext(Dispatchers.IO) {
                    runCatching { historyStore.save(bitmap, result) }
                }
                val historyItems = withContext(Dispatchers.IO) { historyStore.list() }
                if (requestGeneration != imageGeneration) return@launch
                _uiState.update {
                    it.copy(
                        ocrResult = result,
                        ocrInProgress = false,
                        matches = emptyList(),
                        regexError = false,
                        recordId = savedRecord.getOrNull()?.id,
                        recordTitle = savedRecord.getOrNull()?.title.orEmpty(),
                        matchIndex = -1,
                        history = historyItems,
                    )
                }
                if (savedRecord.isFailure) {
                    _messages.tryEmit(message(R.string.record_save_failed))
                }
                if (result.lines.isEmpty()) {
                    _messages.tryEmit(message(R.string.no_text_recognized))
                }
            } catch (error: Exception) {
                if (requestGeneration != imageGeneration) return@launch
                _uiState.update { it.copy(ocrInProgress = false) }
                _messages.tryEmit(message(R.string.ocr_failed))
            }
        }
    }

    fun clearImage() {
        val current = _uiState.value
        queryPersistJob?.cancel()
        queryPersistJob = null
        if (current.recordId != null) {
            val recordId = current.recordId
            val query = current.query
            viewModelScope.launch(Dispatchers.IO) {
                historyStore.updateQuery(recordId, query)
            }
        }
        imageGeneration++
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                image = null,
                ocrResult = null,
                ocrInProgress = false,
                query = "",
                matches = emptyList(),
                regexError = false,
                exporting = false,
                recordId = null,
                recordTitle = "",
                matchIndex = -1,
                matchFocusToken = 0,
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query).applySearch() }
        val recordId = _uiState.value.recordId ?: return
        queryPersistJob?.cancel()
        queryPersistJob = viewModelScope.launch {
            delay(350)
            withContext(Dispatchers.IO) {
                historyStore.updateQuery(recordId, query)
            }
        }
    }

    fun openHistoryRecord(item: HistoryItem) {
        val requestGeneration = ++imageGeneration
        loadJob?.cancel()
        queryPersistJob?.cancel()
        _uiState.update {
            it.copy(
                screen = AppScreen.Home,
                image = null,
                ocrResult = null,
                ocrInProgress = true,
                query = "",
                matches = emptyList(),
                regexError = false,
                recordId = null,
                recordTitle = "",
                matchIndex = -1,
                matchFocusToken = 0,
            )
        }

        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val latest = historyStore.find(item.id) ?: item
                val bitmap = ImageUtils.decodeBitmap(
                    getApplication<Application>(),
                    Uri.fromFile(File(latest.imagePath)),
                )
                val ocr = historyStore.loadOcr(item.id)
                Triple(latest, bitmap, ocr)
            }
            if (requestGeneration != imageGeneration) return@launch

            val (record, bitmap, ocr) = loaded
            if (bitmap == null || ocr == null) {
                _uiState.update { it.copy(ocrInProgress = false, recordId = null) }
                _messages.tryEmit(message(R.string.record_read_failed))
                return@launch
            }

            _uiState.update {
                it.copy(
                    image = bitmap,
                    ocrResult = ocr,
                    ocrInProgress = false,
                    query = record.query,
                    searchOptions = SearchOptions(),
                    recordId = record.id,
                    recordTitle = record.title,
                ).applySearch()
            }
        }
    }

    fun deleteHistoryRecords(ids: Set<String>) {
        if (ids.isEmpty()) return
        queryPersistJob?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.delete(ids) }
            val items = withContext(Dispatchers.IO) { historyStore.list() }
            _uiState.update { state ->
                state.copy(
                    history = items,
                    recordId = state.recordId?.takeUnless { it in ids },
                )
            }
            _messages.tryEmit(message(R.string.record_deleted))
        }
    }

    fun renameHistoryRecord(id: String, title: String) {
        val normalized = title.trim()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.updateTitle(id, normalized) }
            _uiState.update { state ->
                state.copy(
                    history = state.history.map { item ->
                        if (item.id == id) item.copy(title = normalized) else item
                    },
                    recordTitle = if (state.recordId == id) normalized else state.recordTitle,
                )
            }
        }
    }

    fun nextMatch() {
        _uiState.update { state ->
            if (state.matches.isEmpty()) return@update state
            val nextIndex = if (state.matchIndex < 0) {
                0
            } else {
                (state.matchIndex + 1) % state.matches.size
            }
            state.copy(
                matchIndex = nextIndex,
                matchFocusToken = state.matchFocusToken + 1,
            )
        }
    }

    fun previousMatch() {
        _uiState.update { state ->
            if (state.matches.isEmpty()) return@update state
            val previousIndex = if (state.matchIndex <= 0) {
                state.matches.lastIndex
            } else {
                state.matchIndex - 1
            }
            state.copy(
                matchIndex = previousIndex,
                matchFocusToken = state.matchFocusToken + 1,
            )
        }
    }

    fun resetSettings() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    fun exportHighlightedImage() {
        val state = _uiState.value
        val bitmap = state.image ?: return
        if (state.exporting || state.matches.isEmpty()) return

        val exportedMatches = state.matches
        val currentSettings = settings.value

        _uiState.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ImageExporter.export(
                        context = getApplication<Application>(),
                        bitmap = bitmap,
                        matches = exportedMatches,
                        highlightColor = Color(currentSettings.highlightColor),
                        highlightAlpha = currentSettings.highlightAlpha,
                    )
                }
            }
            _uiState.update { it.copy(exporting = false) }

            result
                .onSuccess {
                    _messages.tryEmit(message(R.string.saved))
                }
                .onFailure {
                    _messages.tryEmit(message(R.string.save_failed))
                }
        }
    }

    override fun onCleared() {
        ocrEngine?.close()
        super.onCleared()
    }
}

private fun AppUiState.applySearch(): AppUiState {
    val outcome = ocrResult?.let { OcrMatcher.find(it, query, searchOptions) }
    return copy(
        matches = outcome?.matches.orEmpty(),
        regexError = outcome?.invalidRegex == true,
        matchIndex = -1,
    )
}
