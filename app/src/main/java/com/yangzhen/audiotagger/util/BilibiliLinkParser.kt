package com.yangzhen.audiotagger.util

sealed interface BilibiliVideoRef {
    data class Bv(val bvid: String) : BilibiliVideoRef
    data class Av(val aid: Long) : BilibiliVideoRef
    data class ShortLink(val url: String) : BilibiliVideoRef
}

object BilibiliLinkParser {

    private val BV = Regex("""(?i)(?<![0-9A-Za-z])bv[0-9A-Za-z]{10}(?![0-9A-Za-z])""")
    private val AV = Regex("""(?i)(?<![0-9A-Za-z])av(\d+)(?![0-9A-Za-z])""")
    private val SHORT = Regex("""(?i)(?:https?://)?(?:b23\.tv|bili2233\.cn)/[A-Za-z0-9]+""")

    fun parse(input: String): BilibiliVideoRef? {
        val text = input.trim()
        if (text.isEmpty()) return null

        BV.find(text)?.let { match ->
            return BilibiliVideoRef.Bv("BV" + match.value.substring(2))
        }

        AV.find(text)?.let { match ->
            val aid = match.groupValues[1].toLongOrNull() ?: return@let
            return BilibiliVideoRef.Av(aid)
        }

        SHORT.find(text)?.let { match ->
            val raw = match.value
            val url = when {
                raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
                raw.startsWith("https://") -> raw
                else -> "https://$raw"
            }
            return BilibiliVideoRef.ShortLink(url)
        }

        return null
    }
}
