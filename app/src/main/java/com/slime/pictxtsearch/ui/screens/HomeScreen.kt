package com.slime.pictxtsearch.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slime.pictxtsearch.R
import com.slime.pictxtsearch.data.AppSettings
import com.slime.pictxtsearch.data.HistoryItem
import com.slime.pictxtsearch.model.MatchBox
import com.slime.pictxtsearch.model.OcrResult
import com.slime.pictxtsearch.ui.AppUiState
import com.slime.pictxtsearch.ui.components.ZoomableHighlightedImage
import com.slime.pictxtsearch.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppUiState,
    settings: AppSettings,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleRegex: (Boolean) -> Unit,
    onToggleCaseSensitive: (Boolean) -> Unit,
    onTogglePlainText: (Boolean) -> Unit,
    onExport: () -> Unit,
    onClearImage: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenHistoryRecord: (HistoryItem) -> Unit,
    onDeleteHistoryRecords: (Set<String>) -> Unit,
    onRenameHistoryRecord: (String, String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
) {
    val context = LocalContext.current
    val imageFallback = stringResource(R.string.image_fallback)
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImageSelected(uri)
    }
    val launchPicker: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onExport()
        } else {
            onMessage(context.getString(R.string.storage_permission_required))
        }
    }
    val handleExport: () -> Unit = {
        if (PermissionUtils.needsLegacyStoragePermission() &&
            !PermissionUtils.hasLegacyStoragePermission(context)
        ) {
            @Suppress("DEPRECATION")
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            onExport()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.image != null) {
                            stringResource(R.string.search_page_title, state.recordTitle.ifBlank { imageFallback })
                        } else {
                            "PicTxtSearch"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.image != null) {
                        IconButton(onClick = onClearImage) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.discard_image_description),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val image = state.image
        if (image == null) {
            EmptyHomeContent(
                modifier = Modifier.padding(innerPadding),
                loading = state.ocrInProgress,
                history = state.history,
                onOpenCamera = onOpenCamera,
                onPickImage = launchPicker,
                onOpenRecord = onOpenHistoryRecord,
                onDeleteRecords = onDeleteHistoryRecords,
                onRenameRecord = onRenameHistoryRecord,
            )
        } else {
            LoadedHomeContent(
                modifier = Modifier.padding(innerPadding),
                state = state,
                bitmap = image,
                settings = settings,
                onQueryChange = onQueryChange,
                onToggleRegex = onToggleRegex,
                onToggleCaseSensitive = onToggleCaseSensitive,
                onTogglePlainText = onTogglePlainText,
                onExport = handleExport,
                onMessage = onMessage,
                onPreviousMatch = onPreviousMatch,
                onNextMatch = onNextMatch,
            )
        }
    }
}

@Composable
private fun EmptyHomeContent(
    modifier: Modifier,
    loading: Boolean,
    history: List<HistoryItem>,
    onOpenCamera: () -> Unit,
    onPickImage: () -> Unit,
    onOpenRecord: (HistoryItem) -> Unit,
    onDeleteRecords: (Set<String>) -> Unit,
    onRenameRecord: (String, String) -> Unit,
) {
    if (loading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var renaming by remember { mutableStateOf<HistoryItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    val allIds = history.mapTo(mutableSetOf()) { it.id }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onOpenCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.take_photo))
                    }
                    FilledTonalButton(
                        onClick = onPickImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_photo_library),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_image))
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        Text(
                            text = stringResource(R.string.selected_count, selectedIds.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                selectedIds = if (allIds.isNotEmpty() && selectedIds.size == allIds.size) {
                                    emptySet()
                                } else {
                                    allIds
                                }
                            },
                        ) {
                            Text(
                                if (allIds.isNotEmpty() && selectedIds.size == allIds.size) {
                                    stringResource(R.string.deselect_all)
                                } else {
                                    stringResource(R.string.select_all)
                                },
                            )
                        }
                        TextButton(
                            onClick = { selectedIds = allIds - selectedIds },
                        ) {
                            Text(stringResource(R.string.invert_selection))
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { pendingDelete = selectedIds },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete_selected),
                            )
                        }
                        TextButton(
                            onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.records),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { selectionMode = true }) {
                            Text(stringResource(R.string.select))
                        }
                    }
                }
            }

            items(history, key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onClick = {
                        if (selectionMode) {
                            selectedIds = if (item.id in selectedIds) {
                                selectedIds - item.id
                            } else {
                                selectedIds + item.id
                            }
                        } else {
                            onOpenRecord(item)
                        }
                    },
                    onLongClick = {
                        if (!selectionMode) {
                            selectionMode = true
                            selectedIds = setOf(item.id)
                        } else {
                            selectedIds = if (item.id in selectedIds) {
                                selectedIds - item.id
                            } else {
                                selectedIds + item.id
                            }
                        }
                    },
                    onDelete = { pendingDelete = setOf(item.id) },
                    onRename = {
                        renaming = item
                        renameText = item.title
                    },
                )
            }
        }
    }

    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_record_title)) },
            text = { Text(stringResource(R.string.delete_records_message, ids.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        selectionMode = false
                        selectedIds = emptySet()
                        onDeleteRecords(ids)
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    renaming?.let { record ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_record)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { value ->
                        if (value.length <= 80) renameText = value
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRenameRecord(record.id, renameText.trim())
                        renaming = null
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    item: HistoryItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val thumbnail = remember(item.id, item.thumbPath) {
        BitmapFactory.decodeFile(item.thumbPath)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo_library),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.untitled_record) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatRecordTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                IconButton(onClick = onRename) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.rename_record_description),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.delete_record_description),
                    )
                }
            }
        }
    }
}

private fun formatRecordTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun LoadedHomeContent(
    modifier: Modifier,
    state: AppUiState,
    bitmap: Bitmap,
    settings: AppSettings,
    onQueryChange: (String) -> Unit,
    onToggleRegex: (Boolean) -> Unit,
    onToggleCaseSensitive: (Boolean) -> Unit,
    onTogglePlainText: (Boolean) -> Unit,
    onExport: () -> Unit,
    onMessage: (String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
) {
    val ocrText = remember(state.ocrResult) {
        state.ocrResult?.lines?.joinToString("\n") { it.text }.orEmpty()
    }
    val ocrHighlights = remember(state.ocrResult, state.matches) {
        computeOcrHighlightRanges(state.ocrResult, state.matches)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        SearchToolbar(
            state = state,
            onQueryChange = onQueryChange,
            onToggleRegex = onToggleRegex,
            onToggleCaseSensitive = onToggleCaseSensitive,
            onTogglePlainText = onTogglePlainText,
            onExport = onExport,
        )

        if (state.query.isNotBlank() && !state.regexError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.found_count, state.matches.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.matchIndex in state.matches.indices) {
                    Text(
                        text = stringResource(R.string.match_index, state.matchIndex + 1, state.matches.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(
                    onClick = onPreviousMatch,
                    enabled = state.matches.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.previous))
                }
                TextButton(
                    onClick = onNextMatch,
                    enabled = state.matches.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.next))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            ZoomableHighlightedImage(
                bitmap = bitmap,
                matches = state.matches,
                highlightColor = Color(settings.highlightColor),
                highlightAlpha = settings.highlightAlpha,
                focusMatch = state.matches.getOrNull(state.matchIndex),
                focusToken = state.matchFocusToken,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.ocrInProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        if (ocrText.isNotBlank()) {
            OcrResultPanel(
                text = ocrText,
                highlights = ocrHighlights,
                highlightColor = Color(settings.highlightColor),
                highlightAlpha = settings.highlightAlpha,
                onMessage = onMessage,
            )
        }
    }
}

@Composable
private fun OcrResultPanel(
    text: String,
    highlights: List<IntRange>,
    highlightColor: Color,
    highlightAlpha: Float,
    onMessage: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var value by remember(text) { mutableStateOf(TextFieldValue(text)) }
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.copied)
    val background = highlightColor.copy(
        alpha = (highlightAlpha * 0.7f).coerceIn(0.15f, 0.5f),
    )
    val transformation = remember(highlights, background) {
        HighlightRangesTransformation(highlights, background)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ocr_result),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(
                    if (expanded) R.string.collapse else R.string.expand,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                expandVertically(tween(260, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(140)) +
                shrinkVertically(tween(200, easing = FastOutSlowInEasing)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 220.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            onMessage(copiedMessage)
                        },
                    ) {
                        Text(stringResource(R.string.copy_all))
                    }
                    TextButton(
                        enabled = !value.selection.collapsed,
                        onClick = {
                            val start = value.selection.min.coerceIn(0, value.text.length)
                            val end = value.selection.max.coerceIn(start, value.text.length)
                            if (start < end) {
                                clipboard.setText(
                                    AnnotatedString(value.text.substring(start, end)),
                                )
                                onMessage(copiedMessage)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.copy_selected))
                    }
                }
            }
        }
    }
}

private class HighlightRangesTransformation(
    private val ranges: List<IntRange>,
    private val background: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (ranges.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val annotated = buildAnnotatedString {
            append(text.text)
            ranges.forEach { range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(0, text.length)
                if (start < end) {
                    addStyle(SpanStyle(background = background), start, end)
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

private fun computeOcrHighlightRanges(
    ocr: OcrResult?,
    matches: List<MatchBox>,
): List<IntRange> {
    if (ocr == null || matches.isEmpty()) return emptyList()
    val lineStarts = IntArray(ocr.lines.size)
    var offset = 0
    ocr.lines.forEachIndexed { index, line ->
        lineStarts[index] = offset
        offset += line.text.length
        if (index < ocr.lines.lastIndex) offset += 1
    }
    return matches.mapNotNull { match ->
        if (match.lineIndex !in ocr.lines.indices || match.range.isEmpty()) {
            null
        } else {
            val start = lineStarts[match.lineIndex] + match.range.first
            val end = lineStarts[match.lineIndex] + match.range.last + 1
            if (start in 0 until offset && end <= offset && start < end) {
                start until end
            } else {
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchToolbar(
    state: AppUiState,
    onQueryChange: (String) -> Unit,
    onToggleRegex: (Boolean) -> Unit,
    onToggleCaseSensitive: (Boolean) -> Unit,
    onTogglePlainText: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val hasActiveOption = state.searchOptions.regex ||
        state.searchOptions.caseSensitive ||
        state.searchOptions.plainText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            enabled = state.ocrResult != null && !state.ocrInProgress,
            placeholder = {
                Text(
                    stringResource(
                        if (state.ocrInProgress) R.string.recognizing else R.string.search,
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            isError = state.regexError,
            supportingText = if (state.regexError) {
                { Text(stringResource(R.string.invalid_regex)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )

        Box {
            FilledTonalIconButton(
                onClick = { optionsExpanded = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.search_options),
                    tint = if (hasActiveOption) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                )
            }
            DropdownMenu(
                expanded = optionsExpanded,
                onDismissRequest = { optionsExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.regex)) },
                    onClick = { onToggleRegex(!state.searchOptions.regex) },
                    trailingIcon = {
                        Checkbox(
                            checked = state.searchOptions.regex,
                            onCheckedChange = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plain_text)) },
                    onClick = { onTogglePlainText(!state.searchOptions.plainText) },
                    trailingIcon = {
                        Checkbox(
                            checked = state.searchOptions.plainText,
                            onCheckedChange = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.case_sensitive)) },
                    onClick = { onToggleCaseSensitive(!state.searchOptions.caseSensitive) },
                    trailingIcon = {
                        Checkbox(
                            checked = state.searchOptions.caseSensitive,
                            onCheckedChange = null,
                        )
                    },
                )
            }
        }

        FilledTonalIconButton(
            onClick = onExport,
            enabled = state.matches.isNotEmpty() && !state.exporting,
        ) {
            if (state.exporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = stringResource(R.string.save_highlight_image),
                )
            }
        }
    }
}
