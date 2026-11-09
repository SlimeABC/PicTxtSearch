package com.slime.pictxtsearch.model

import kotlin.math.abs


data class TextBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun union(other: TextBox): TextBox = TextBox(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )
}

data class TextPoint(
    val x: Float,
    val y: Float,
)


data class TextQuad(
    val points: List<TextPoint>,
)


fun List<TextPoint>.isAxisAlignedPolygon(tolerance: Float = 2f): Boolean {
    if (size != 4) return false
    for (index in indices) {
        val a = this[index]
        val b = this[(index + 1) % size]
        val dx = abs(a.x - b.x)
        val dy = abs(a.y - b.y)
        if (dx > tolerance && dy > tolerance) return false
    }
    return true
}


data class OcrSymbol(
    val text: String,
    val box: TextBox,
    val quad: TextQuad? = null,
)


data class OcrElement(
    val text: String,
    val box: TextBox,
    val symbols: List<OcrSymbol> = emptyList(),
    val quad: TextQuad? = null,
)


data class OcrLine(
    val text: String,
    val box: TextBox,
    val elements: List<OcrElement>,
)

data class OcrResult(
    val text: String,
    val lines: List<OcrLine>,
) {
    val elementCount: Int get() = lines.sumOf { it.elements.size }

    companion object {
        val EMPTY = OcrResult(text = "", lines = emptyList())
    }
}


data class MatchBox(
    val text: String,
    val box: TextBox,
    val lineIndex: Int = -1,
    val range: IntRange = IntRange.EMPTY,
    val polygon: List<TextPoint> = emptyList(),
)

data class SearchOptions(
    val regex: Boolean = false,
    val caseSensitive: Boolean = false,
    val plainText: Boolean = false,
)

data class SearchOutcome(
    val matches: List<MatchBox>,
    val invalidRegex: Boolean = false,
)
