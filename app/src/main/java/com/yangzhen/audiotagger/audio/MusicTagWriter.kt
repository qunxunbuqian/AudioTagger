package com.yangzhen.audiotagger.audio

import com.yangzhen.audiotagger.util.Mp4Container
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

object MusicTagWriter {

    enum class Status { SUCCESS, SKIPPED, FAILED }

    data class Outcome(
        val status: Status,
        val message: String? = null,
        val finalFile: File? = null,
        val warning: String? = null,
    )

    data class BasicTags(
        val artist: String?,
        val album: String?,
    )

    private val MP4_EXTENSIONS = setOf("m4a", "m4b", "mp4")

    fun configureLogging() {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").apply {
            level = Level.WARNING
            handlers?.forEach { it.level = Level.WARNING }
        }
    }

    fun readArtist(file: File): String? = readBasicTags(file)?.artist

    fun readBasicTags(file: File): BasicTags? {
        if (Mp4Container.isMp4(file) && Mp4Container.isFragmentedMp4(file)) return null
        val id3Tags = try {
            val tag = AudioFileIO.read(file).tag
            if (tag == null) {
                null
            } else {
                val artist = tag.getFirst(FieldKey.ARTIST)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: tag.getFirst(FieldKey.ALBUM_ARTIST)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                val album = tag.getFirst(FieldKey.ALBUM)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                BasicTags(artist = artist, album = album)
            }
        } catch (_: Throwable) {
            null
        }
        val wavInfo = if (file.extension.lowercase(Locale.ROOT) == "wav") {
            readWavInfoTags(file)
        } else {
            null
        }
        if (id3Tags == null && wavInfo == null) return null
        return BasicTags(
            artist = id3Tags?.artist ?: wavInfo?.artist,
            album = id3Tags?.album ?: wavInfo?.album,
        )
    }

    private fun readWavInfoTags(file: File): BasicTags? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            if (raf.read(header) != header.size) return@use null
            if (String(header, 0, 4, Charsets.ISO_8859_1) != "RIFF" ||
                String(header, 8, 4, Charsets.ISO_8859_1) != "WAVE"
            ) {
                return@use null
            }

            var position = 12L
            val length = raf.length()
            while (position + 8L <= length) {
                raf.seek(position)
                val chunkId = readFourCc(raf) ?: break
                val chunkSize = readLittleEndianInt(raf).toLong() and 0xFFFF_FFFFL
                val dataStart = position + 8L
                if (dataStart + chunkSize > length) break

                if (chunkId == "LIST" && chunkSize >= 4L) {
                    raf.seek(dataStart)
                    val listType = readFourCc(raf)
                    if (listType == "INFO") {
                        val values = readInfoValues(raf, dataStart + 4L, dataStart + chunkSize)
                        if (values.isNotEmpty()) {
                            return@use BasicTags(
                                artist = values["IART"],
                                album = values["IPRD"],
                            )
                        }
                    }
                }
                position = dataStart + chunkSize + (chunkSize % 2L)
            }
            null
        }
    }.getOrNull()

    private fun readInfoValues(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
    ): Map<String, String> {
        val values = HashMap<String, String>()
        var position = start
        while (position + 8L <= end) {
            raf.seek(position)
            val id = readFourCc(raf) ?: break
            val size = readLittleEndianInt(raf).toLong() and 0xFFFF_FFFFL
            val dataStart = position + 8L
            if (dataStart + size > end) break
            if (id in setOf("IART", "INAM", "IPRD")) {
                val bytes = ByteArray(size.toInt())
                raf.readFully(bytes)
                val value = String(bytes, Charsets.UTF_8)
                    .trimEnd('\u0000', ' ', '\r', '\n')
                    .trim()
                if (value.isNotBlank()) values[id] = value
            }
            position = dataStart + size + (size % 2L)
        }
        return values
    }

    private fun readFourCc(raf: RandomAccessFile): String? {
        val bytes = ByteArray(4)
        if (raf.read(bytes) != bytes.size) return null
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun readLittleEndianInt(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return 0
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    @JvmOverloads
    fun write(
        file: File,
        artist: String,
        album: String,
        title: String,
        coverJpeg: ByteArray?,
        writeTitle: Boolean,
        writeAlbum: Boolean,
        skipIfDone: Boolean,
        validator: ((File) -> String?)? = null,
        audioOnly: Boolean = false,
    ): Outcome {
        if (!file.exists()) return Outcome(Status.FAILED, "文件不存在")
        if (!file.isFile) return Outcome(Status.FAILED, "不是文件")
        if (!file.canRead()) return Outcome(Status.FAILED, "无法读取文件")
        if (!file.canWrite()) return Outcome(Status.FAILED, "文件只读，无法写入")
        if (artist.isBlank()) return Outcome(Status.FAILED, "歌手名为空")

        return try {
            if (Mp4Container.isMp4(file)) {
                writeMp4(
                    file, artist, album, title, coverJpeg,
                    writeTitle, writeAlbum, skipIfDone, validator, audioOnly,
                )
            } else {
                writeSafely(
                    file, artist, album, title, coverJpeg,
                    writeTitle, writeAlbum, skipIfDone, validator,
                )
            }
        } catch (t: Throwable) {
            Outcome(Status.FAILED, "写入失败：${reasonOf(t)}")
        }
    }

    private fun writeSafely(
        file: File,
        artist: String,
        album: String,
        title: String,
        coverJpeg: ByteArray?,
        writeTitle: Boolean,
        writeAlbum: Boolean,
        skipIfDone: Boolean,
        validator: ((File) -> String?)?,
    ): Outcome {
        val parent = file.parentFile ?: return Outcome(Status.FAILED, "找不到文件所在文件夹")
        val extension = file.extension.lowercase(Locale.ROOT).ifBlank { "tmp" }
        val temp = try {
            File.createTempFile(".audiotagger-", ".$extension", parent)
        } catch (t: Throwable) {
            return Outcome(Status.FAILED, "创建临时文件失败：${reasonOf(t)}")
        }
        return try {
            file.copyTo(temp, overwrite = true)
            val outcome = try {
                writeTags(
                    temp, artist, album, title, coverJpeg,
                    writeTitle, writeAlbum, skipIfDone,
                )
            } catch (t: Throwable) {
                return Outcome(Status.FAILED, "标签写入失败：${reasonOf(t)}")
            }
            if (outcome.status == Status.FAILED) return outcome
            validator?.invoke(temp)?.let { reason ->
                return Outcome(Status.FAILED, "写入后音轨校验失败：$reason")
            }
            replaceFile(temp, file)
            outcome.copy(finalFile = file)
        } catch (t: Throwable) {
            Outcome(Status.FAILED, "保存文件失败：${reasonOf(t)}")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun writeMp4(
        file: File,
        artist: String,
        album: String,
        title: String,
        coverJpeg: ByteArray?,
        writeTitle: Boolean,
        writeAlbum: Boolean,
        skipIfDone: Boolean,
        validator: ((File) -> String?)?,
        audioOnly: Boolean,
    ): Outcome {
        val extension = file.extension.lowercase(Locale.ROOT)
        val fragmented = Mp4Container.isFragmentedMp4(file)
        val needsRewrite = fragmented || audioOnly || extension !in MP4_EXTENSIONS
        if (!needsRewrite) {
            return writeSafely(
                file, artist, album, title, coverJpeg,
                writeTitle, writeAlbum, skipIfDone, validator,
            )
        }

        val parent = file.parentFile ?: return Outcome(Status.FAILED, "找不到文件所在文件夹")
        val target = if (extension in MP4_EXTENSIONS) file else uniqueM4aTarget(file)
        val temp = try {
            File.createTempFile(".audiotagger-", ".m4a", parent)
        } catch (t: Throwable) {
            return Outcome(Status.FAILED, "创建临时文件失败：${reasonOf(t)}")
        }
        return try {
            try {
                if (fragmented || audioOnly) {
                    Mp4Remuxer.remux(file, temp, audioOnly)
                } else {
                    file.copyTo(temp, overwrite = true)
                }
            } catch (t: Throwable) {
                return Outcome(Status.FAILED, "音频修复失败：${reasonOf(t)}")
            }

            val outcome = try {
                writeTags(
                    temp, artist, album, title, coverJpeg,
                    writeTitle, writeAlbum, skipIfDone,
                )
            } catch (t: Throwable) {
                return Outcome(Status.FAILED, "修复后写标签失败：${reasonOf(t)}")
            }
            if (outcome.status == Status.FAILED) return outcome
            validator?.invoke(temp)?.let { reason ->
                return Outcome(Status.FAILED, "写入后音轨校验失败：$reason")
            }

            if (target.absolutePath == file.absolutePath) {
                replaceFile(temp, file)
                outcome.copy(finalFile = file)
            } else {
                try {
                    moveFile(temp, target)
                } catch (t: Throwable) {
                    return Outcome(Status.FAILED, "生成 M4A 文件失败：${reasonOf(t)}")
                }
                val warning = if (file.exists() && !file.delete()) {
                    "已生成 ${target.name}，但原文件 ${file.name} 删除失败"
                } else {
                    null
                }
                outcome.copy(
                    message = warning ?: outcome.message,
                    finalFile = target,
                    warning = warning,
                )
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun writeTags(
        file: File,
        artist: String,
        album: String,
        title: String,
        coverJpeg: ByteArray?,
        writeTitle: Boolean,
        writeAlbum: Boolean,
        skipIfDone: Boolean,
    ): Outcome {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.getTagOrCreateAndSetDefault()

        if (skipIfDone) {
            val existingArtist = tag.getFirst(FieldKey.ARTIST).orEmpty()
            val existingAlbumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).orEmpty()
            val existingTitle = tag.getFirst(FieldKey.TITLE).orEmpty()
            val existingAlbum = tag.getFirst(FieldKey.ALBUM).orEmpty()
            val hasArtwork = tag.getFirstArtwork() != null
            val artistMatches = existingArtist.equals(artist, ignoreCase = true)
            val albumArtistMatches = existingAlbumArtist.equals(artist, ignoreCase = true)
            val titleMatches = !writeTitle || existingTitle.equals(title, ignoreCase = true)
            val albumMatches = !writeAlbum || album.isBlank() ||
                existingAlbum.equals(album, ignoreCase = true)
            val coverMatches = coverJpeg == null || hasArtwork
            if (artistMatches && albumArtistMatches && titleMatches && albumMatches && coverMatches) {
                return Outcome(Status.SKIPPED)
            }
        }

        tag.setField(FieldKey.ARTIST, artist)
        tag.setField(FieldKey.ALBUM_ARTIST, artist)
        if (writeAlbum && album.isNotBlank()) {
            tag.setField(FieldKey.ALBUM, album)
        }
        if (writeTitle && title.isNotBlank()) {
            tag.setField(FieldKey.TITLE, title)
        }

        if (coverJpeg != null && coverJpeg.isNotEmpty()) {
            tag.deleteArtworkField()
            val artwork = ArtworkFactory.getNew()
            artwork.setBinaryData(coverJpeg)
            artwork.setMimeType("image/jpeg")
            artwork.setDescription("Cover")
            artwork.setPictureType(3)
            tag.setField(artwork)
        }

        audioFile.commit()
        return Outcome(Status.SUCCESS)
    }

    private fun uniqueM4aTarget(file: File): File {
        val parent = file.parentFile ?: error("找不到文件所在文件夹")
        val base = file.nameWithoutExtension
        var candidate = File(parent, "$base.m4a")
        var index = 2
        while (candidate.exists()) {
            candidate = File(parent, "$base ($index).m4a")
            index++
        }
        return candidate
    }

    private fun replaceFile(temp: File, target: File) {
        val parent = target.parentFile ?: error("找不到文件所在文件夹")
        val backup = File(parent, ".audiotagger-backup-${System.nanoTime()}.bak")
        if (!target.renameTo(backup)) {
            moveFile(temp, target)
            return
        }
        try {
            moveFile(temp, target)
            backup.delete()
        } catch (t: Throwable) {
            runCatching { if (target.exists()) target.delete() }
            runCatching { backup.renameTo(target) }
            throw t
        }
    }

    private fun moveFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            return
        } catch (_: Exception) {
            // Some external storage providers do not support atomic moves.
        }

        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return
        } catch (_: Exception) {
            // Fall back to a plain copy below.
        }

        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun reasonOf(t: Throwable): String {
        val parts = ArrayList<String>(3)
        var current: Throwable? = t
        while (current != null && parts.size < 3) {
            val exception = current
            val message = exception.message?.takeIf { it.isNotBlank() }
                ?: exception.javaClass.simpleName
            val location = exception.stackTrace.take(4).joinToString(" <- ") { frame ->
                val line = frame.lineNumber.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
                "${frame.className.substringAfterLast('.')}.${frame.methodName}$line"
            }.takeIf { it.isNotBlank() }
            parts += if (location == null) {
                "${exception.javaClass.simpleName}: $message"
            } else {
                "${exception.javaClass.simpleName}: $message（$location）"
            }
            val cause = exception.cause
            current = if (cause == null || cause === exception) null else cause
        }
        return parts.joinToString(" <- ")
    }
}
