package com.slime.pictxtsearch.data

import android.content.Context
import android.graphics.Bitmap
import com.slime.pictxtsearch.model.OcrResult
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val id: String,
    val createdAt: Long,
    val title: String,
    val query: String,
    val imagePath: String,
    val thumbPath: String,
)


class HistoryStore(context: Context) {

    private val root = File(context.filesDir, "history")
    private val indexFile = File(root, "index.json")

    @Synchronized
    fun list(): List<HistoryItem> = readIndex().sortedByDescending { it.createdAt }

    @Synchronized
    fun find(id: String): HistoryItem? = readIndex().firstOrNull { it.id == id }

    @Synchronized
    fun save(image: Bitmap, ocr: OcrResult, query: String = ""): HistoryItem {
        if (!root.exists() && !root.mkdirs()) {
            throw IOException("无法创建记录目录")
        }
        val id = UUID.randomUUID().toString()
        val directory = File(root, id)
        if (!directory.mkdirs()) throw IOException("无法创建记录")

        try {
            val imageFile = File(directory, "image.jpg")
            val imageSaved = imageFile.outputStream().use {
                image.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            if (!imageSaved) throw IOException("图片保存失败")

            val thumbFile = File(directory, "thumb.jpg")
            val thumbScale = THUMB_SIZE.toFloat() / max(image.width, image.height)
            val thumb = if (thumbScale < 1f) {
                Bitmap.createScaledBitmap(
                    image,
                    (image.width * thumbScale).roundToInt().coerceAtLeast(1),
                    (image.height * thumbScale).roundToInt().coerceAtLeast(1),
                    true,
                )
            } else {
                image
            }
            val thumbSaved = thumbFile.outputStream().use {
                thumb.compress(Bitmap.CompressFormat.JPEG, 86, it)
            }
            if (thumb !== image) thumb.recycle()
            if (!thumbSaved) throw IOException("缩略图保存失败")

            File(directory, "ocr.json").writeText(OcrJson.encode(ocr))

            val item = HistoryItem(
                id = id,
                createdAt = System.currentTimeMillis(),
                title = ocr.lines.firstOrNull { it.text.isNotBlank() }
                    ?.text
                    ?.take(120)
                    .orEmpty(),
                query = query,
                imagePath = imageFile.absolutePath,
                thumbPath = thumbFile.absolutePath,
            )
            val items = readIndex().toMutableList()
            items.add(item)
            writeIndex(items)
            return item
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun updateQuery(id: String, query: String) {
        val items = readIndex()
        if (items.none { it.id == id }) return
        writeIndex(items.map { item -> if (item.id == id) item.copy(query = query) else item })
    }

    @Synchronized
    fun updateTitle(id: String, title: String) {
        val items = readIndex()
        if (items.none { it.id == id }) return
        writeIndex(items.map { item -> if (item.id == id) item.copy(title = title) else item })
    }

    @Synchronized
    fun loadOcr(id: String): OcrResult? {
        val file = File(File(root, id), "ocr.json")
        if (!file.exists()) return null
        return runCatching { OcrJson.decode(file.readText()) }.getOrNull()
    }

    @Synchronized
    fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        ids.forEach { id -> File(root, id).deleteRecursively() }
        writeIndex(readIndex().filterNot { it.id in ids })
    }

    private fun readIndex(): List<HistoryItem> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val id = obj.optString("id")
                    if (id.isEmpty()) continue
                    val directory = File(root, id)
                    val image = File(directory, "image.jpg")
                    if (!image.exists()) continue
                    val thumb = File(directory, "thumb.jpg")
                    add(
                        HistoryItem(
                            id = id,
                            createdAt = obj.optLong("createdAt"),
                            title = obj.optString("title").ifBlank { obj.optString("preview") },
                            query = obj.optString("query"),
                            imagePath = image.absolutePath,
                            thumbPath = if (thumb.exists()) thumb.absolutePath else image.absolutePath,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(items: List<HistoryItem>) {
        if (!root.exists() && !root.mkdirs()) return
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("createdAt", item.createdAt)
                    .put("query", item.query)
                    .put("title", item.title),
            )
        }
        val temp = File(root, "index.json.tmp")
        temp.writeText(array.toString())
        temp.copyTo(indexFile, overwrite = true)
        temp.delete()
    }

    private companion object {
        const val THUMB_SIZE = 180
    }
}
