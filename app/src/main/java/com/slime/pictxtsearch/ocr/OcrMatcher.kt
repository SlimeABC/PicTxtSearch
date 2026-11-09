package com.slime.pictxtsearch.ocr

import com.slime.pictxtsearch.model.MatchBox
import com.slime.pictxtsearch.model.OcrElement
import com.slime.pictxtsearch.model.OcrLine
import com.slime.pictxtsearch.model.OcrResult
import com.slime.pictxtsearch.model.SearchOptions
import com.slime.pictxtsearch.model.SearchOutcome
import com.slime.pictxtsearch.model.TextBox
import com.slime.pictxtsearch.model.TextPoint
import com.slime.pictxtsearch.model.TextQuad


object OcrMatcher {

    private const val MAX_MATCHES = 300

    fun find(
        result: OcrResult,
        rawQuery: String,
        options: SearchOptions = SearchOptions(),
    ): SearchOutcome {
        val query = if (options.regex) rawQuery else rawQuery.trim()
        if (query.isEmpty()) return SearchOutcome(emptyList())

        val plainTextMode = options.plainText && !options.regex
        val normalizedQuery = if (plainTextMode) normalize(query) else ""
        if (plainTextMode && normalizedQuery.isEmpty()) return SearchOutcome(emptyList())

        val regex = if (options.regex) {
            runCatching {
                Regex(
                    pattern = query,
                    options = if (options.caseSensitive) {
                        emptySet()
                    } else {
                        setOf(RegexOption.IGNORE_CASE)
                    },
                )
            }.getOrElse {
                return SearchOutcome(emptyList(), invalidRegex = true)
            }
        } else {
            null
        }

        val matches = mutableListOf<MatchBox>()
        result.lines.forEachIndexed { lineIndex, line ->
            if (matches.size >= MAX_MATCHES) return@forEachIndexed

            if (line.text.isBlank()) {
                line.elements.forEach { element ->
                    val hit = if (regex != null) {
                        regex.containsMatchIn(element.text)
                    } else {
                        element.text.contains(query, ignoreCase = !options.caseSensitive)
                    }
                    if (hit && matches.size < MAX_MATCHES) {
                        matches += MatchBox(
                            text = element.text,
                            box = element.box,
                            lineIndex = lineIndex,
                            polygon = element.quad?.points?.let(::convexHull).orEmpty(),
                        )
                    }
                }
                return@forEachIndexed
            }

            val ranges = mapElementRanges(line)
            val charBoxes = mapCharacterBoxes(line, ranges)
            val charQuads = mapCharacterQuads(line, ranges)

            when {
                plainTextMode -> {
                    val (normalizedLine, mapping) = normalizeWithMapping(line.text)
                    if (normalizedLine.isEmpty()) return@forEachIndexed

                    var index = normalizedLine.indexOf(
                        string = normalizedQuery,
                        ignoreCase = !options.caseSensitive,
                    )
                    while (index >= 0 && matches.size < MAX_MATCHES) {
                        val normalizedEnd = index + normalizedQuery.length - 1
                        if (normalizedEnd < mapping.size) {
                            val start = mapping[index]
                            val end = mapping[normalizedEnd]
                            if (start <= end) {
                                val range = start..end
                                matches += MatchBox(
                                    text = line.text.substring(range),
                                    box = unionBox(charBoxes, range, line),
                                    lineIndex = lineIndex,
                                    range = range,
                                    polygon = polygonForRange(charQuads, range),
                                )
                            }
                        }
                        index = normalizedLine.indexOf(
                            string = normalizedQuery,
                            startIndex = index + 1,
                            ignoreCase = !options.caseSensitive,
                        )
                    }
                }

                regex != null -> {
                    regex.findAll(line.text).forEach { match ->
                        if (matches.size >= MAX_MATCHES) return@forEach
                        val range = match.range
                        if (range.isEmpty()) return@forEach
                        matches += MatchBox(
                            text = line.text.substring(range),
                            box = unionBox(charBoxes, range, line),
                            lineIndex = lineIndex,
                            range = range,
                            polygon = polygonForRange(charQuads, range),
                        )
                    }
                }

                else -> {
                    var index = line.text.indexOf(query, ignoreCase = !options.caseSensitive)
                    while (index >= 0 && matches.size < MAX_MATCHES) {
                        val range = index..(index + query.length - 1)
                        matches += MatchBox(
                            text = line.text.substring(range),
                            box = unionBox(charBoxes, range, line),
                            lineIndex = lineIndex,
                            range = range,
                            polygon = polygonForRange(charQuads, range),
                        )
                        index = line.text.indexOf(
                            string = query,
                            startIndex = index + 1,
                            ignoreCase = !options.caseSensitive,
                        )
                    }
                }
            }
        }
        return SearchOutcome(matches)
    }

    private fun normalize(text: String): String = buildString(text.length) {
        text.forEach { char ->
            if (Character.isLetterOrDigit(char)) append(char)
        }
    }

    
    private fun normalizeWithMapping(text: String): Pair<String, IntArray> {
        val builder = StringBuilder(text.length)
        val mapping = IntArray(text.length)
        text.forEachIndexed { index, char ->
            if (Character.isLetterOrDigit(char)) {
                mapping[builder.length] = index
                builder.append(char)
            }
        }
        return builder.toString() to mapping.copyOf(builder.length)
    }

    private fun unionBox(
        charBoxes: Array<TextBox?>,
        charRange: IntRange,
        line: OcrLine,
    ): TextBox = (charRange.first..charRange.last)
        .mapNotNull { charBoxes.getOrNull(it) }
        .reduceOrNull { a, b -> a.union(b) }
        ?: line.box

    private fun polygonForRange(
        charQuads: Array<TextQuad?>,
        charRange: IntRange,
    ): List<TextPoint> {
        val points = mutableListOf<TextPoint>()
        for (index in charRange) {
            charQuads.getOrNull(index)?.points?.let { points += it }
        }
        return convexHull(points)
    }

    private fun convexHull(points: List<TextPoint>): List<TextPoint> {
        if (points.size < 3) return emptyList()
        val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size < 3) return emptyList()

        fun cross(o: TextPoint, a: TextPoint, b: TextPoint): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

        val lower = mutableListOf<TextPoint>()
        sorted.forEach { point ->
            while (lower.size >= 2 &&
                cross(lower[lower.size - 2], lower.last(), point) <= 0f
            ) {
                lower.removeAt(lower.lastIndex)
            }
            lower += point
        }

        val upper = mutableListOf<TextPoint>()
        sorted.asReversed().forEach { point ->
            while (upper.size >= 2 &&
                cross(upper[upper.size - 2], upper.last(), point) <= 0f
            ) {
                upper.removeAt(upper.lastIndex)
            }
            upper += point
        }

        val hull = lower.dropLast(1) + upper.dropLast(1)
        return if (hull.size >= 3) hull else emptyList()
    }

    
    private fun mapCharacterBoxes(
        line: OcrLine,
        ranges: List<ElementRange>,
    ): Array<TextBox?> {
        val boxes = arrayOfNulls<TextBox>(line.text.length)
        ranges.forEach { (element, elementRange) ->
            if (element.symbols.isEmpty()) {
                elementRange.forEach { lineIndex ->
                    if (lineIndex in boxes.indices) boxes[lineIndex] = element.box
                }
                return@forEach
            }

            val elementText = element.text
            val covered = BooleanArray(elementText.length)
            var cursor = 0
            element.symbols.forEach { symbol ->
                if (symbol.text.isEmpty()) return@forEach
                val start = elementText.indexOf(symbol.text, cursor)
                if (start < 0) return@forEach
                cursor = start + symbol.text.length
                symbol.text.indices.forEach { offset ->
                    val textIndex = start + offset
                    if (textIndex !in elementText.indices) return@forEach
                    val lineIndex = elementRange.first + textIndex
                    if (lineIndex in boxes.indices) {
                        boxes[lineIndex] = symbol.box
                        covered[textIndex] = true
                    }
                }
            }

            elementText.indices.forEach { textIndex ->
                if (!covered[textIndex]) {
                    val lineIndex = elementRange.first + textIndex
                    if (lineIndex in boxes.indices) boxes[lineIndex] = element.box
                }
            }
        }
        return boxes
    }

    
    private fun mapCharacterQuads(
        line: OcrLine,
        ranges: List<ElementRange>,
    ): Array<TextQuad?> {
        val quads = arrayOfNulls<TextQuad>(line.text.length)
        ranges.forEach { (element, elementRange) ->
            if (element.symbols.isEmpty()) {
                element.quad?.let { quad ->
                    elementRange.forEach { lineIndex ->
                        if (lineIndex in quads.indices) quads[lineIndex] = quad
                    }
                }
                return@forEach
            }

            val elementText = element.text
            val covered = BooleanArray(elementText.length)
            var cursor = 0
            element.symbols.forEach symbolLoop@ { symbol ->
                val quad = symbol.quad ?: return@symbolLoop
                if (symbol.text.isEmpty()) return@symbolLoop
                val start = elementText.indexOf(symbol.text, cursor)
                if (start < 0) return@symbolLoop
                cursor = start + symbol.text.length
                symbol.text.indices.forEach charLoop@ { offset ->
                    val textIndex = start + offset
                    if (textIndex !in elementText.indices) return@charLoop
                    val lineIndex = elementRange.first + textIndex
                    if (lineIndex in quads.indices) {
                        quads[lineIndex] = quad
                        covered[textIndex] = true
                    }
                }
            }

            element.quad?.let { fallback ->
                elementText.indices.forEach { textIndex ->
                    if (!covered[textIndex]) {
                        val lineIndex = elementRange.first + textIndex
                        if (lineIndex in quads.indices) quads[lineIndex] = fallback
                    }
                }
            }
        }
        return quads
    }

    
    private fun mapElementRanges(line: OcrLine): List<ElementRange> {
        val result = mutableListOf<ElementRange>()
        var cursor = 0
        line.elements.forEach { element ->
            if (element.text.isEmpty()) return@forEach
            val start = line.text.indexOf(element.text, cursor)
            if (start < 0) return@forEach
            cursor = start + element.text.length
            result += ElementRange(element, start until cursor)
        }
        return result
    }

    private data class ElementRange(
        val element: OcrElement,
        val range: IntRange,
    )
}


fun TextBox.scaled(scale: Float, offsetX: Float, offsetY: Float): TextBox = TextBox(
    left = (left * scale + offsetX).toInt(),
    top = (top * scale + offsetY).toInt(),
    right = (right * scale + offsetX).toInt(),
    bottom = (bottom * scale + offsetY).toInt(),
)
