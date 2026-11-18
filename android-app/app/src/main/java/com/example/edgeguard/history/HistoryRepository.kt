package com.example.edgeguard.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Local detection history. Stored in a small JSON file inside the app's private folder,
 * so it never leaves the phone (matches the "On-device only" screen in the design).
 *
 * - Results older than 3 months are deleted automatically (design: "Past results are
 *   automatically deleted after 3 months").
 * - No Room / KSP on purpose: no new Gradle plugins, so it cannot break the build.
 *   If the history ever gets big, this class can be swapped for Room without changing the UI.
 *
 * Usage:
 *   val repo = HistoryRepository.get(context)
 *   repo.add(DetectionType.AI_LABEL, "ocr_label")
 *   val list by repo.records.collectAsState()
 */
class HistoryRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()

    private val _records = MutableStateFlow<List<DetectionRecord>>(emptyList())

    /** Newest first. */
    val records: StateFlow<List<DetectionRecord>> = _records

    init {
        synchronized(lock) {
            val cleaned = purge(load())
            _records.value = cleaned
            save(cleaned)
        }
    }

    fun add(type: DetectionType, source: String, timeMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val current = purge(_records.value)
            val nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
            val updated = (listOf(DetectionRecord(nextId, timeMillis, type, source)) + current)
                .sortedByDescending { it.timeMillis }
            _records.value = updated
            save(updated)
        }
    }

    /** Remove everything older than 3 months. Call this when the app opens. */
    fun purgeOld() {
        synchronized(lock) {
            val cleaned = purge(_records.value)
            if (cleaned.size != _records.value.size) {
                _records.value = cleaned
                save(cleaned)
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            _records.value = emptyList()
            save(emptyList())
        }
    }

    // ---- internals ----

    private fun purge(list: List<DetectionRecord>): List<DetectionRecord> {
        val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -RETENTION_MONTHS) }.timeInMillis
        return list.filter { it.timeMillis >= cutoff }.sortedByDescending { it.timeMillis }
    }

    private fun load(): List<DetectionRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DetectionRecord(
                    id = o.getLong("id"),
                    timeMillis = o.getLong("t"),
                    type = DetectionType.valueOf(o.getString("type")),
                    source = o.optString("src", "")
                )
            }
        } catch (e: Exception) {
            emptyList() // a damaged file must never crash the app
        }
    }

    private fun save(list: List<DetectionRecord>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("t", it.timeMillis)
                        .put("type", it.type.name)
                        .put("src", it.source)
                )
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) { // fallback if rename is refused
                file.writeText(arr.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // history is nice-to-have; never crash detection because of a disk problem
        }
    }

    companion object {
        private const val FILE_NAME = "history.json"
        private const val RETENTION_MONTHS = 3

        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** "videos watched" on the History summary = every saved result. */
val List<DetectionRecord>.watchedCount: Int get() = size

/** "deepfakes detected" on the History summary = results of type FAKE. */
val List<DetectionRecord>.fakeCount: Int get() = count { it.type == DetectionType.FAKE }
