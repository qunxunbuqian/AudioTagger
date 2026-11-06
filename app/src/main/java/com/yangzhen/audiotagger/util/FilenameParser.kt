package com.yangzhen.audiotagger.util

import java.util.Locale

data class ParsedName(val artist: String, val title: String)

data class ScannedTrack(
    val file: java.io.File,
    val parsed: ParsedName,
    val manualArtist: Boolean = false,
    val folderArtist: Boolean = false,
    val embeddedAlbum: String = "",
)

object FilenameParser {

    private val SUPPORTED = setOf(
        "mp3", "m4a", "m4b", "flac", "ogg", "oga", "opus", "wav", "aiff", "aif"
    )

    private val AUDIO_LIKE = SUPPORTED + setOf("wma", "ape", "wv", "dsf", "dff", "aac")

    private val JUNK = Regex("""\s*[\[(]\s*mqms\d*\s*[\])]\s*""", RegexOption.IGNORE_CASE)
    private val TRACK_PREFIX = Regex("""^\s*\d{1,3}[.、]\s*""")
    private val MULTI_SPACE = Regex("""\s+""")

    private val GENERIC_FOLDERS = setOf(
        "music", "音乐", "歌曲", "download", "downloads", "下载", "手机音乐",
        "audio", "audios", "sound", "sounds", "sdcard", "storage", "emulated",
        "0", "recordings", "录音", "miui", "tencent", "qqmusic", "netease",
        "cloudmusic", "wechat", "微信", "files", "文档", "documents"
    )

    fun isSupportedAudio(fileName: String): Boolean = extensionOf(fileName) in SUPPORTED

    fun isAudioLike(fileName: String): Boolean = extensionOf(fileName) in AUDIO_LIKE

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun cleanName(fileName: String): String {
        var base = fileName.trim()
        while (true) {
            val ext = extensionOf(base)
            if (ext in SUPPORTED) {
                base = base.substringBeforeLast('.')
            } else {
                break
            }
        }
        base = JUNK.replace(base, " ")
        base = TRACK_PREFIX.replace(base, "")
        return MULTI_SPACE.replace(base, " ").trim()
    }

    fun parse(fileName: String, parentFolder: String?, isRootFolder: Boolean): ParsedName {
        val base = cleanName(fileName)
        val spaced = base.indexOf(" - ")
        if (spaced > 0) {
            val left = base.substring(0, spaced).trim()
            val right = base.substring(spaced + 3).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                return ParsedName(left, right)
            }
        }

        val hyphens = base.indices.filter { base[it] == '-' }
        if (hyphens.isNotEmpty()) {
            val index = if (hyphens.size == 1) hyphens.first() else hyphens.last()
            val left = base.substring(0, index).trim()
            val right = base.substring(index + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                return ParsedName(left, right)
            }
        }

        val folder = parentFolder?.trim().orEmpty()
        val artist = if (folder.isNotEmpty() && !isRootFolder && normalize(folder) !in GENERIC_FOLDERS) {
            folder
        } else {
            "未知歌手"
        }
        return ParsedName(artist, base.ifBlank { fileName })
    }

    fun isGenericFolder(name: String): Boolean = normalize(name) in GENERIC_FOLDERS

    private val UNKNOWN_ARTISTS = setOf(
        "未知歌手", "unknown", "unknown artist", "unknownartist",
        "various artists", "various artist", "va", "<unknown>"
    )

    fun isUnknownArtistName(value: String?): Boolean =
        value.isNullOrBlank() || normalize(value) in UNKNOWN_ARTISTS

    fun normalize(value: String): String =
        MULTI_SPACE.replace(value.trim(), " ").lowercase(Locale.ROOT)

    /**
     * Some downloads are named "song - artist" instead of "artist - song".
     * If the right side is an artist that appears at least twice in the library,
     * swap the two sides. This fixes examples such as "一定会 - 林俊杰".
     */
    fun fixReversedNames(items: List<ScannedTrack>): List<ScannedTrack> {
        val unknown = normalize("未知歌手")
        val counts = items
            .map { normalize(it.parsed.artist) }
            .filter { it.isNotEmpty() && it != unknown }
            .groupingBy { it }
            .eachCount()
        val knownArtists = counts.filterValues { it >= 2 }.keys
        if (knownArtists.isEmpty()) return items

        return items.map { item ->
            if (item.manualArtist || item.folderArtist) return@map item
            val artistKey = normalize(item.parsed.artist)
            val titleKey = normalize(item.parsed.title)
            if (titleKey in knownArtists && artistKey !in knownArtists && artistKey != unknown) {
                item.copy(parsed = ParsedName(item.parsed.title, item.parsed.artist))
            } else {
                item
            }
        }
    }
}



