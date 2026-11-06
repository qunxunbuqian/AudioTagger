package com.yangzhen.audiotagger.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.security.MessageDigest

object StorageUtils {

    private val COVER_NAMES = listOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
        "album.jpg", "album.jpeg", "album.png", "albumart.jpg", "albumart.png",
        "front.jpg", "front.jpeg", "front.png",
        "封面.jpg", "封面.jpeg", "封面.png", "专辑封面.jpg", "专辑封面.png"
    )

    @Suppress("DEPRECATION")
    fun primaryRoot(): File = Environment.getExternalStorageDirectory()

    fun roots(context: Context): List<File> {
        val result = linkedSetOf<File>()
        result.add(primaryRoot())
        context.getExternalFilesDirs(null).forEach { dir ->
            val path = dir?.absolutePath ?: return@forEach
            val beforeAndroid = path.substringBefore("/Android/", missingDelimiterValue = "")
            if (beforeAndroid.isNotEmpty() && beforeAndroid != "/") {
                result.add(File(beforeAndroid))
            }
        }
        return result.filter { it.isDirectory }.sortedBy { it.absolutePath }
    }

    fun findCover(folder: File): File? {
        if (!folder.isDirectory) return null
        val files = folder.listFiles()?.filter { it.isFile } ?: return null
        for (name in COVER_NAMES) {
            files.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun commonAncestor(files: List<File>): File? {
        if (files.isEmpty()) return null
        val parents = files.mapNotNull { it.parentFile?.absolutePath?.trimEnd('/') }
        if (parents.isEmpty()) return null
        val split = parents.map { it.split('/') }
        var common = split.first()
        for (parts in split.drop(1)) {
            common = common.zip(parts).takeWhile { it.first == it.second }.map { it.first }
            if (common.isEmpty()) return null
        }
        val path = common.joinToString("/").ifEmpty { "/" }
        return File(path)
    }

    fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
