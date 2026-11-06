package com.yangzhen.audiotagger.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BilibiliCover(
    val jpeg: ByteArray,
    val title: String,
    val bvid: String,
    val coverUrl: String,
    val cid: Long = 0,
    val duration: Int = 0,
)

object BilibiliCoverClient {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"
    private const val TIMEOUT_MS = 20_000
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024

    private data class VideoInfo(
        val title: String,
        val bvid: String,
        val picUrl: String,
        val cid: Long,
        val duration: Int,
    )

    suspend fun fetch(input: String): Result<BilibiliCover> = withContext(Dispatchers.IO) {
        runCatching {
            val ref = BilibiliLinkParser.parse(input)
                ?: error("没有找到 BV 号或 av 号，请粘贴 B 站视频链接")
            val video = resolveVideoRef(ref)
            val info = fetchVideoInfo(video)
            val imageBytes = downloadImage(info.picUrl)
            val jpeg = CoverProcessor.loadFromBytes(imageBytes).getOrElse { error ->
                error("封面图片处理失败：${error.message ?: "未知错误"}")
            }
            BilibiliCover(
                jpeg = jpeg,
                title = info.title,
                bvid = info.bvid,
                coverUrl = info.picUrl,
                cid = info.cid,
                duration = info.duration,
            )
        }
    }

    private fun resolveVideoRef(ref: BilibiliVideoRef): BilibiliVideoRef {
        if (ref !is BilibiliVideoRef.ShortLink) return ref
        val finalUrl = resolveShortLink(ref.url)
        return when (val resolved = BilibiliLinkParser.parse(finalUrl)) {
            is BilibiliVideoRef.Bv -> resolved
            is BilibiliVideoRef.Av -> resolved
            else -> error("短链接没有跳转到 B 站视频")
        }
    }

    private fun fetchVideoInfo(ref: BilibiliVideoRef): VideoInfo {
        val query = when (ref) {
            is BilibiliVideoRef.Bv -> "bvid=${URLEncoder.encode(ref.bvid, "UTF-8")}"
            is BilibiliVideoRef.Av -> "aid=${ref.aid}"
            is BilibiliVideoRef.ShortLink -> error("短链接还未解析")
        }
        val body = requestText("https://api.bilibili.com/x/web-interface/view?$query")
        val json = JSONObject(body)
        val code = json.optInt("code", -1)
        if (code != 0) {
            val message = json.optString("message").ifBlank { "code=$code" }
            error("B站接口返回：$message")
        }
        val data = json.optJSONObject("data") ?: error("B站没有返回视频信息")
        val pic = data.optString("pic").trim()
        if (pic.isBlank()) error("这个视频没有可用封面")
        val bvid = data.optString("bvid").ifBlank {
            (ref as? BilibiliVideoRef.Bv)?.bvid.orEmpty()
        }
        val title = data.optString("title").ifBlank { "未命名视频" }
        val cid = data.optLong("cid", 0L).takeIf { it > 0L }
            ?: data.optJSONArray("pages")
                ?.optJSONObject(0)
                ?.optLong("cid", 0L)
                ?.takeIf { it > 0L }
            ?: 0L
        val duration = data.optInt("duration", 0)
        return VideoInfo(
            title = title,
            bvid = bvid,
            picUrl = normalizeHttps(pic),
            cid = cid,
            duration = duration,
        )
    }

    private fun requestText(url: String): String {
        val connection = openConnection(url)
        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                error("B站接口请求失败（HTTP $code）")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadImage(url: String): ByteArray {
        val connection = openConnection(url)
        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                error("封面下载失败（HTTP $code）")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_IMAGE_BYTES) error("封面图片过大")

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) error("封面图片过大")
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveShortLink(shortUrl: String): String {
        var current = normalizeHttps(shortUrl)
        repeat(5) {
            val connection = openConnection(current)
            connection.instanceFollowRedirects = false
            try {
                val code = connection.responseCode
                if (code !in 300..399) return current
                val location = connection.getHeaderField("Location") ?: return current
                current = normalizeHttps(URL(URL(current), location).toString())
            } finally {
                connection.disconnect()
            }
        }
        error("短链接跳转次数过多")
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(normalizeHttps(url)).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Referer", REFERER)
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        return connection
    }

    private fun normalizeHttps(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        url.startsWith("https://") -> url
        else -> "https://$url"
    }
}
