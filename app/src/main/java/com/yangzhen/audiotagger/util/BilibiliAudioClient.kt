package com.yangzhen.audiotagger.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BilibiliAudioSource(
    val title: String,
    val bvid: String,
    val cid: Long,
    val duration: Int,
    val coverJpeg: ByteArray?,
    val audioUrls: List<String>,
    val mimeType: String,
    val referer: String,
    val cookie: String?,
)

object BilibiliAudioClient {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val API_TIMEOUT_MS = 20_000
    private const val DOWNLOAD_TIMEOUT_MS = 60_000
    private const val MAX_AUDIO_BYTES = 512L * 1024L * 1024L

    private data class AudioStream(
        val urls: List<String>,
        val mimeType: String,
    )

    suspend fun fetch(input: String): Result<BilibiliAudioSource> = withContext(Dispatchers.IO) {
        runCatching {
            val video = BilibiliCoverClient.fetch(input).getOrElse { throw it }
            if (video.cid <= 0L) error("这个视频没有可下载的音频分P")
            val referer = "https://www.bilibili.com/video/${video.bvid}"
            val cookie = fetchBuvidCookie()
            val audio = fetchAudioStream(video.bvid, video.cid, cookie)
            BilibiliAudioSource(
                title = video.title,
                bvid = video.bvid,
                cid = video.cid,
                duration = video.duration,
                coverJpeg = video.jpeg,
                audioUrls = audio.urls,
                mimeType = audio.mimeType,
                referer = referer,
                cookie = cookie,
            )
        }
    }

    suspend fun download(source: BilibiliAudioSource, target: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (url in source.audioUrls) {
                try {
                    downloadOne(url, target, source.referer, source.cookie)
                    return@withContext Result.success(Unit)
                } catch (t: Throwable) {
                    lastError = t
                    runCatching { target.delete() }
                }
            }
            Result.failure(lastError ?: IllegalStateException("没有可用的音频下载地址"))
        }

    private fun fetchAudioStream(
        bvid: String,
        cid: Long,
        cookie: String?,
    ): AudioStream {
        val urls = LinkedHashSet<String>()
        var lastError: Throwable? = null

        try {
            urls += fetchDashUrls(bvid, cid, cookie)
        } catch (t: Throwable) {
            lastError = t
        }
        try {
            urls += fetchDurlUrls(bvid, cid, cookie)
        } catch (t: Throwable) {
            if (lastError == null) lastError = t
        }
        if (urls.isEmpty()) {
            throw lastError ?: IllegalStateException("这个视频没有可下载的音频")
        }
        return AudioStream(urls = urls.toList(), mimeType = "audio/mp4")
    }

    private fun fetchDashUrls(bvid: String, cid: Long, cookie: String?): List<String> {
        val query = "bvid=${URLEncoder.encode(bvid, "UTF-8")}&cid=$cid&fnval=16&fourk=1"
        val body = requestText(
            "https://api.bilibili.com/x/player/playurl?$query",
            accept = "application/json, text/plain, */*",
            cookie = cookie,
        )
        val json = JSONObject(body)
        val code = json.optInt("code", -1)
        if (code != 0) {
            val message = json.optString("message").ifBlank { "code=$code" }
            error("B站音频接口返回：$message")
        }
        val data = json.optJSONObject("data") ?: error("B站没有返回播放信息")
        val audios = data.optJSONObject("dash")?.optJSONArray("audio")
            ?: error("这个视频没有提供独立音频轨")
        val best = (0 until audios.length())
            .mapNotNull { index -> audios.optJSONObject(index) }
            .maxByOrNull { stream -> stream.optInt("bandwidth", 0) }
            ?: error("这个视频没有可用音轨")

        val url = best.optString("baseUrl").ifBlank { best.optString("base_url") }
        if (url.isBlank()) error("B站没有返回音频下载地址")
        return (listOf(url) +
            stringList(best.optJSONArray("backupUrl")) +
            stringList(best.optJSONArray("backup_url"))).distinct()
    }

    private fun fetchDurlUrls(bvid: String, cid: Long, cookie: String?): List<String> {
        val query = "bvid=${URLEncoder.encode(bvid, "UTF-8")}&cid=$cid&fnval=16&fourk=1" +
            "&platform=html5&high_quality=1"
        val body = requestText(
            "https://api.bilibili.com/x/player/playurl?$query",
            accept = "application/json, text/plain, */*",
            cookie = cookie,
        )
        val json = JSONObject(body)
        val code = json.optInt("code", -1)
        if (code != 0) {
            val message = json.optString("message").ifBlank { "code=$code" }
            error("B站备用音频接口返回：$message")
        }
        val data = json.optJSONObject("data") ?: error("B站没有返回备用播放信息")
        val durl = data.optJSONArray("durl") ?: error("B站没有返回备用音频地址")
        return (0 until durl.length()).flatMap { index ->
            val item = durl.optJSONObject(index) ?: return@flatMap emptyList()
            listOf(item.optString("url")) +
                stringList(item.optJSONArray("backup_url")) +
                stringList(item.optJSONArray("backupUrl"))
        }.filter { it.isNotBlank() }.distinct()
    }

    private fun downloadOne(
        url: String,
        target: File,
        referer: String,
        cookie: String?,
    ) {
        val connection = openConnection(
            url,
            DOWNLOAD_TIMEOUT_MS,
            "audio/mp4, audio/*, */*",
            referer,
            cookie,
        )
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val host = runCatching { URL(url).host }.getOrDefault("未知 CDN")
                error("音频下载失败（HTTP $code，CDN：$host）")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_AUDIO_BYTES) error("音频文件过大")

            var total = 0L
            target.outputStream().buffered().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_AUDIO_BYTES) error("音频文件过大")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (total <= 0L) error("下载到的音频为空")
        } catch (t: Throwable) {
            runCatching { target.delete() }
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun requestText(url: String, accept: String, cookie: String? = null): String {
        val connection = openConnection(
            url,
            API_TIMEOUT_MS,
            accept,
            "https://www.bilibili.com/",
            cookie,
        )
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

    private fun openConnection(
        url: String,
        timeoutMs: Int,
        accept: String,
        referer: String,
        cookie: String?,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = API_TIMEOUT_MS
        connection.readTimeout = timeoutMs
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Referer", referer)
        connection.setRequestProperty("Origin", "https://www.bilibili.com")
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        if (!cookie.isNullOrBlank()) {
            connection.setRequestProperty("Cookie", cookie)
        }
        return connection
    }

    private fun fetchBuvidCookie(): String? = runCatching {
        val body = requestText(
            "https://api.bilibili.com/x/frontend/finger/spi",
            accept = "application/json, text/plain, */*",
        )
        val data = JSONObject(body).optJSONObject("data") ?: return@runCatching null
        val b3 = data.optString("b_3").trim()
        val b4 = data.optString("b_4").trim()
        val parts = buildList {
            if (b3.isNotBlank()) add("buvid3=$b3")
            if (b4.isNotBlank()) add("buvid4=$b4")
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }.getOrNull()

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
    }
}
