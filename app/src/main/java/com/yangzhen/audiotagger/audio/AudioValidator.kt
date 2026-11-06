package com.yangzhen.audiotagger.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.util.Locale

object AudioValidator {

    private val SUPPORTED = setOf(
        "mp3", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "wav",
    )

    fun validate(file: File): String? {
        if (file.extension.lowercase(Locale.ROOT) !in SUPPORTED) return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasAudio = false
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    break
                }
            }
            if (hasAudio) null else "没有检测到可播放的音频轨"
        } catch (t: Throwable) {
            t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        } finally {
            runCatching { extractor.release() }
        }
    }
}
