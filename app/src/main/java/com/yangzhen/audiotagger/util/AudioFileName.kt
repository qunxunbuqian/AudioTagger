package com.yangzhen.audiotagger.util

/**
 * Builds safe `artist - title` file names without touching the filesystem.
 */
object AudioFileName {

    private const val MAX_FILE_NAME_BYTES = 200
    private const val SEPARATOR = " - "

    private val ILLEGAL = mapOf(
        '<' to '＜',
        '>' to '＞',
        ':' to '：',
        '"' to '＂',
        '/' to '／',
        '\\' to '＼',
        '|' to '｜',
        '?' to '？',
        '*' to '＊',
    )
    private val CONTROL = Regex("[\\u0000-\\u001F\\u007F]")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizePart(value: String): String {
        val replaced = buildString(value.length) {
            value.forEach { character -> append(ILLEGAL[character] ?: character) }
        }
        return WHITESPACE.replace(CONTROL.replace(replaced, " "), " ")
            .trim()
            .trimEnd('.', ' ')
    }

    fun buildFileName(artist: String, title: String, extension: String): String {
        val safeArtist = sanitizePart(artist).ifBlank { "未知歌手" }
        val safeTitle = sanitizePart(title).ifBlank { "未命名" }
        val suffix = extension.trim()
            .removePrefix(".")
            .takeIf { value -> value.isNotEmpty() && value.all { it.isLetterOrDigit() } }
            ?.let { ".$it" }
            .orEmpty()

        val maxBaseBytes = MAX_FILE_NAME_BYTES - utf8Size(suffix)
        val artistPart = truncateUtf8(safeArtist, maxBaseBytes / 2)
        val titleBudget = maxBaseBytes - utf8Size(artistPart) - utf8Size(SEPARATOR)
        val titlePart = truncateUtf8(safeTitle, titleBudget.coerceAtLeast(3))
            .ifBlank { "未命名" }
        return artistPart + SEPARATOR + titlePart + suffix
    }

    private fun utf8Size(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        var used = 0
        var index = 0
        val result = StringBuilder()
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val piece = String(Character.toChars(codePoint))
            val size = utf8Size(piece)
            if (used + size > maxBytes) break
            result.append(piece)
            used += size
            index += Character.charCount(codePoint)
        }
        return result.toString().trimEnd()
    }
}
