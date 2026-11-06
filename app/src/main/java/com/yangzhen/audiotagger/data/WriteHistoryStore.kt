package com.yangzhen.audiotagger.data

import com.yangzhen.audiotagger.TrackWriteState
import com.yangzhen.audiotagger.WriteRecord
import org.json.JSONObject
import java.io.File

class WriteHistoryStore(private val file: File) {

    @Synchronized
    fun append(record: WriteRecord) {
        runCatching {
            val json = JSONObject().apply {
                put("finishedAt", record.finishedAt)
                put("path", record.path)
                put("fileName", record.fileName)
                put("artist", record.artist)
                put("album", record.album)
                put("state", record.state.name)
                put("message", record.message.orEmpty())
            }
            file.appendText(json.toString() + "\n", Charsets.UTF_8)
            if (file.length() > MAX_FILE_BYTES) {
                val kept = file.readLines(Charsets.UTF_8).takeLast(MAX_LINES)
                file.writeText(kept.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }
        }
    }

    @Synchronized
    fun load(limit: Int = 800): List<WriteRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines(Charsets.UTF_8)
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parse(line) }
                .toList()
                .takeLast(limit)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun updatePaths(changes: Map<String, Pair<String, String>>) {
        if (changes.isEmpty() || !file.isFile) return
        runCatching {
            val updated = file.readLines(Charsets.UTF_8).map { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val json = JSONObject(line)
                    val change = changes[json.optString("path")] ?: return@map line
                    json.put("path", change.first)
                    json.put("fileName", change.second)
                    json.toString()
                }
            }
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(updated.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
                temporary.delete()
            }
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }

    private fun parse(line: String): WriteRecord? = runCatching {
        val json = JSONObject(line)
        WriteRecord(
            finishedAt = json.optLong("finishedAt", 0L),
            path = json.optString("path"),
            fileName = json.optString("fileName"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            state = TrackWriteState.valueOf(json.optString("state")),
            message = json.optString("message").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    private companion object {
        const val MAX_FILE_BYTES = 4_000_000L
        const val MAX_LINES = 3000
    }
}
