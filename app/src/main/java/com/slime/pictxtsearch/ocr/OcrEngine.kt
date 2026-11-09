package com.slime.pictxtsearch.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.slime.pictxtsearch.model.OcrElement
import com.slime.pictxtsearch.model.OcrLine
import com.slime.pictxtsearch.model.OcrResult
import com.slime.pictxtsearch.model.OcrSymbol
import com.slime.pictxtsearch.model.TextBox
import com.slime.pictxtsearch.model.TextPoint
import com.slime.pictxtsearch.model.TextQuad
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers


class OcrEngine : Closeable {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    )

    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    if (continuation.isActive) continuation.resume(text.toOcrResult())
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }

    override fun close() {
        recognizer.close()
    }
}

private fun Text.toOcrResult(): OcrResult {
    val lines = mutableListOf<OcrLine>()
    textBlocks.forEach { block ->
        block.lines.forEach { line ->
            val lineBox = line.boundingBox?.toTextBox() ?: return@forEach
            val elements = line.elements.mapNotNull { element ->
                element.boundingBox?.let { elementBox ->
                    val elementQuad = element.cornerPoints
                        ?.takeIf { it.size >= 3 }
                        ?.let { corners ->
                            TextQuad(corners.map { TextPoint(it.x.toFloat(), it.y.toFloat()) })
                        }
                    val symbols = element.symbols.mapNotNull { symbol ->
                        symbol.boundingBox?.let { symbolBox ->
                            val symbolQuad = symbol.cornerPoints
                                ?.takeIf { it.size >= 3 }
                                ?.let { corners ->
                                    TextQuad(
                                        corners.map { TextPoint(it.x.toFloat(), it.y.toFloat()) },
                                    )
                                }
                            OcrSymbol(symbol.text, symbolBox.toTextBox(), symbolQuad)
                        }
                    }
                    OcrElement(element.text, elementBox.toTextBox(), symbols, elementQuad)
                }
            }
            if (line.text.isBlank() && elements.isEmpty()) return@forEach
            lines += OcrLine(
                text = line.text.ifBlank { elements.joinToString("") { it.text } },
                box = lineBox,
                elements = elements,
            )
        }
    }
    return OcrResult(text = text, lines = lines)
}

private fun android.graphics.Rect.toTextBox(): TextBox = TextBox(left, top, right, bottom)
