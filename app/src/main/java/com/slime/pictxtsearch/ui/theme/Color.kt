package com.slime.pictxtsearch.ui.theme

import androidx.compose.ui.graphics.Color


data class HighlightPreset(
    val name: String,
    val color: Color,
)

val HighlightPresets: List<HighlightPreset> = listOf(
    HighlightPreset("琥珀", Color(0xFFFFC107)),
    HighlightPreset("珊瑚", Color(0xFFFF7043)),
    HighlightPreset("朱红", Color(0xFFE53935)),
    HighlightPreset("桃粉", Color(0xFFFF4081)),
    HighlightPreset("翠绿", Color(0xFF43A047)),
    HighlightPreset("青色", Color(0xFF00ACC1)),
    HighlightPreset("天蓝", Color(0xFF1E88E5)),
    HighlightPreset("紫罗兰", Color(0xFF8E24AA)),
)

const val DEFAULT_HIGHLIGHT_COLOR: Int = 0xFFFFC107.toInt()
const val DEFAULT_HIGHLIGHT_ALPHA: Float = 0.35f
