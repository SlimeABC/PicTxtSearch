package com.slime.pictxtsearch.data

import com.slime.pictxtsearch.model.OcrElement
import com.slime.pictxtsearch.model.OcrLine
import com.slime.pictxtsearch.model.OcrResult
import com.slime.pictxtsearch.model.OcrSymbol
import com.slime.pictxtsearch.model.TextBox
import com.slime.pictxtsearch.model.TextPoint
import com.slime.pictxtsearch.model.TextQuad
import org.json.JSONArray
import org.json.JSONObject


object OcrJson {

    fun encode(result: OcrResult): String = JSONObject().apply {
        put("text", result.text)
        put(
            "lines",
            JSONArray().apply {
                result.lines.forEach { line ->
                    put(line.toJson())
                }
            },
        )
    }.toString()

    fun decode(json: String): OcrResult? = runCatching {
        val root = JSONObject(json)
        val linesArray = root.optJSONArray("lines") ?: JSONArray()
        val lines = buildList {
            for (index in 0 until linesArray.length()) {
                val line = linesArray.getJSONObject(index)
                val elementsArray = line.optJSONArray("elements") ?: JSONArray()
                val elements = buildList {
                    for (elementIndex in 0 until elementsArray.length()) {
                        val element = elementsArray.getJSONObject(elementIndex)
                        val symbolsArray = element.optJSONArray("symbols") ?: JSONArray()
                        val symbols = buildList {
                            for (symbolIndex in 0 until symbolsArray.length()) {
                                val symbol = symbolsArray.getJSONObject(symbolIndex)
                                add(
                                    OcrSymbol(
                                        text = symbol.optString("text"),
                                        box = symbol.optJSONObject("box")?.toBox()
                                            ?: TextBox(0, 0, 0, 0),
                                        quad = symbol.optJSONObject("quad")?.toQuad(),
                                    ),
                                )
                            }
                        }
                        add(
                            OcrElement(
                                text = element.optString("text"),
                                box = element.optJSONObject("box")?.toBox()
                                    ?: TextBox(0, 0, 0, 0),
                                symbols = symbols,
                                quad = element.optJSONObject("quad")?.toQuad(),
                            ),
                        )
                    }
                }
                add(
                    OcrLine(
                        text = line.optString("text"),
                        box = line.optJSONObject("box")?.toBox() ?: TextBox(0, 0, 0, 0),
                        elements = elements,
                    ),
                )
            }
        }
        OcrResult(text = root.optString("text"), lines = lines)
    }.getOrNull()

    private fun OcrLine.toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("box", box.toJson())
        put(
            "elements",
            JSONArray().apply {
                elements.forEach { element ->
                    put(element.toJson())
                }
            },
        )
    }

    private fun OcrElement.toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("box", box.toJson())
        quad?.let { put("quad", it.toJson()) }
        put(
            "symbols",
            JSONArray().apply {
                symbols.forEach { symbol ->
                    put(symbol.toJson())
                }
            },
        )
    }

    private fun OcrSymbol.toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("box", box.toJson())
        quad?.let { put("quad", it.toJson()) }
    }

    private fun TextBox.toJson(): JSONObject = JSONObject()
        .put("l", left)
        .put("t", top)
        .put("r", right)
        .put("b", bottom)

    private fun TextQuad.toJson(): JSONObject = JSONObject().put(
        "points",
        JSONArray().apply {
            points.forEach { point ->
                put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble()))
            }
        },
    )

    private fun JSONObject.toBox(): TextBox = TextBox(
        left = optInt("l"),
        top = optInt("t"),
        right = optInt("r"),
        bottom = optInt("b"),
    )

    private fun JSONObject.toQuad(): TextQuad? {
        val array = optJSONArray("points") ?: return null
        val points = buildList {
            for (index in 0 until array.length()) {
                val point = array.getJSONObject(index)
                add(TextPoint(point.optDouble("x").toFloat(), point.optDouble("y").toFloat()))
            }
        }
        return if (points.size >= 3) TextQuad(points) else null
    }
}
