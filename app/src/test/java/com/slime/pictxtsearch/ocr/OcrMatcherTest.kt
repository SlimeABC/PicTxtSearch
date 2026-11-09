package com.slime.pictxtsearch.ocr

import com.slime.pictxtsearch.model.OcrElement
import com.slime.pictxtsearch.model.OcrLine
import com.slime.pictxtsearch.model.OcrResult
import com.slime.pictxtsearch.model.OcrSymbol
import com.slime.pictxtsearch.model.SearchOptions
import com.slime.pictxtsearch.model.TextBox
import com.slime.pictxtsearch.model.TextPoint
import com.slime.pictxtsearch.model.TextQuad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrMatcherTest {

    @Test
    fun `matches english word case-insensitively and returns its element box`() {
        val hello = OcrElement("Hello", TextBox(10, 10, 60, 30))
        val world = OcrElement("world", TextBox(70, 10, 130, 30))
        val line = OcrLine("Hello world", TextBox(10, 10, 130, 30), listOf(hello, world))
        val result = OcrResult("Hello world", listOf(line))

        val matches = OcrMatcher.find(result, "WORLD").matches

        assertEquals(1, matches.size)
        assertEquals("world", matches[0].text)
        assertEquals(world.box, matches[0].box)
        assertEquals(0, matches[0].lineIndex)
        assertEquals(6..10, matches[0].range)
    }

    @Test
    fun `highlights only matched characters inside a word`() {
        val symbols = listOf(
            OcrSymbol("q", TextBox(0, 0, 10, 20)),
            OcrSymbol("u", TextBox(10, 0, 20, 20)),
            OcrSymbol("i", TextBox(20, 0, 30, 20)),
            OcrSymbol("c", TextBox(30, 0, 40, 20)),
            OcrSymbol("k", TextBox(40, 0, 50, 20)),
        )
        val element = OcrElement("quick", TextBox(0, 0, 50, 20), symbols)
        val line = OcrLine("quick", TextBox(0, 0, 50, 20), listOf(element))
        val result = OcrResult("quick", listOf(line))

        val match = OcrMatcher.find(result, "ui").matches.single()

        assertEquals(TextBox(10, 0, 30, 20), match.box)
        assertEquals(0, match.lineIndex)
        assertEquals(1..2, match.range)
    }

    @Test
    fun `plain text search ignores punctuation and highlights whole segment`() {
        val line = OcrLine(
            "123，45",
            TextBox(0, 0, 120, 20),
            listOf(OcrElement("123，45", TextBox(0, 0, 120, 20))),
        )
        val result = OcrResult("123，45", listOf(line))

        val match = OcrMatcher.find(
            result,
            "12345",
            SearchOptions(plainText = true),
        ).matches.single()

        assertEquals("123，45", match.text)
        assertEquals(0..5, match.range)
        assertEquals(line.box, match.box)
        assertTrue(OcrMatcher.find(result, "12345").matches.isEmpty())
    }

    @Test
    fun `rotated symbol corners produce a rotated polygon`() {
        val quad = TextQuad(
            listOf(
                TextPoint(10f, 0f),
                TextPoint(20f, 10f),
                TextPoint(10f, 20f),
                TextPoint(0f, 10f),
            ),
        )
        val symbol = OcrSymbol("A", TextBox(0, 0, 20, 20), quad)
        val element = OcrElement("A", TextBox(0, 0, 20, 20), listOf(symbol), quad)
        val line = OcrLine("A", TextBox(0, 0, 20, 20), listOf(element))
        val result = OcrResult("A", listOf(line))

        val match = OcrMatcher.find(result, "A").matches.single()

        assertEquals(4, match.polygon.size)
        assertEquals(quad.points.toSet(), match.polygon.toSet())
    }

    @Test
    fun `matches chinese phrase spanning character elements`() {
        val elements = listOf(
            OcrElement("你", TextBox(0, 0, 25, 20)),
            OcrElement("好", TextBox(25, 0, 50, 20)),
            OcrElement("世", TextBox(50, 0, 75, 20)),
            OcrElement("界", TextBox(75, 0, 100, 20)),
        )
        val line = OcrLine("你好世界", TextBox(0, 0, 100, 20), elements)
        val result = OcrResult("你好世界", listOf(line))

        val matches = OcrMatcher.find(result, "好世").matches

        assertEquals(1, matches.size)
        assertEquals("好世", matches[0].text)
        assertEquals(TextBox(25, 0, 75, 20), matches[0].box)
    }

    @Test
    fun `finds every occurrence in reading order`() {
        val line1 = OcrLine(
            "cat cat",
            TextBox(0, 0, 100, 20),
            listOf(
                OcrElement("cat", TextBox(0, 0, 30, 20)),
                OcrElement("cat", TextBox(60, 0, 90, 20)),
            ),
        )
        val line2 = OcrLine(
            "cat",
            TextBox(0, 30, 30, 50),
            listOf(OcrElement("cat", TextBox(0, 30, 30, 50))),
        )
        val result = OcrResult("cat cat\ncat", listOf(line1, line2))

        val matches = OcrMatcher.find(result, "cat").matches

        assertEquals(3, matches.size)
        assertEquals(TextBox(60, 0, 90, 20), matches[1].box)
        assertEquals(TextBox(0, 30, 30, 50), matches[2].box)
    }

    @Test
    fun `falls back to the line box when elements cannot be mapped`() {
        val line = OcrLine("abc", TextBox(5, 6, 55, 26), emptyList())
        val result = OcrResult("abc", listOf(line))

        val matches = OcrMatcher.find(result, "b").matches

        assertEquals(1, matches.size)
        assertEquals(line.box, matches[0].box)
    }

    @Test
    fun `blank query returns no matches`() {
        val line = OcrLine("text", TextBox(0, 0, 10, 10), emptyList())
        val result = OcrResult("text", listOf(line))

        assertTrue(OcrMatcher.find(result, "   ").matches.isEmpty())
        assertTrue(OcrMatcher.find(result, "").matches.isEmpty())
    }

    @Test
    fun `case sensitive search distinguishes case`() {
        val line = OcrLine(
            "Cat cat",
            TextBox(0, 0, 100, 20),
            listOf(
                OcrElement("Cat", TextBox(0, 0, 35, 20)),
                OcrElement("cat", TextBox(60, 0, 95, 20)),
            ),
        )
        val result = OcrResult("Cat cat", listOf(line))

        val sensitive = OcrMatcher.find(
            result,
            "Cat",
            SearchOptions(caseSensitive = true),
        ).matches
        val insensitive = OcrMatcher.find(
            result,
            "Cat",
            SearchOptions(caseSensitive = false),
        ).matches

        assertEquals(1, sensitive.size)
        assertEquals(TextBox(0, 0, 35, 20), sensitive[0].box)
        assertEquals(2, insensitive.size)
    }

    @Test
    fun `regex search returns each pattern match with its box`() {
        val line = OcrLine(
            "abc 123 def 456",
            TextBox(0, 0, 200, 20),
            listOf(
                OcrElement("abc", TextBox(0, 0, 40, 20)),
                OcrElement("123", TextBox(50, 0, 90, 20)),
                OcrElement("def", TextBox(100, 0, 140, 20)),
                OcrElement("456", TextBox(150, 0, 190, 20)),
            ),
        )
        val result = OcrResult("abc 123 def 456", listOf(line))

        val matches = OcrMatcher.find(
            result,
            "\\d+",
            SearchOptions(regex = true),
        ).matches

        assertEquals(2, matches.size)
        assertEquals("123", matches[0].text)
        assertEquals(TextBox(50, 0, 90, 20), matches[0].box)
        assertEquals("456", matches[1].text)
        assertEquals(TextBox(150, 0, 190, 20), matches[1].box)
    }

    @Test
    fun `regex respects case sensitivity option`() {
        val line = OcrLine("Hello", TextBox(0, 0, 80, 20), emptyList())
        val result = OcrResult("Hello", listOf(line))

        assertEquals(
            1,
            OcrMatcher.find(result, "h.llo", SearchOptions(regex = true)).matches.size,
        )
        assertEquals(
            0,
            OcrMatcher.find(
                result,
                "h.llo",
                SearchOptions(regex = true, caseSensitive = true),
            ).matches.size,
        )
    }

    @Test
    fun `invalid regex reports error and returns no matches`() {
        val line = OcrLine("text", TextBox(0, 0, 40, 20), emptyList())
        val result = OcrResult("text", listOf(line))

        val outcome = OcrMatcher.find(result, "[", SearchOptions(regex = true))

        assertTrue(outcome.invalidRegex)
        assertTrue(outcome.matches.isEmpty())
    }

    @Test
    fun `normal search does not report regex errors`() {
        val line = OcrLine("a[b", TextBox(0, 0, 40, 20), emptyList())
        val result = OcrResult("a[b", listOf(line))

        val outcome = OcrMatcher.find(result, "[", SearchOptions(regex = false))

        assertFalse(outcome.invalidRegex)
        assertEquals(1, outcome.matches.size)
    }
}
