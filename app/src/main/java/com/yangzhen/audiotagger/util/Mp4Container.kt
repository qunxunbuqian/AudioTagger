package com.yangzhen.audiotagger.util

import java.io.File
import java.io.RandomAccessFile

object Mp4Container {

    fun isMp4(file: File): Boolean {
        if (!file.isFile) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(8)
                if (raf.read(header) < 8) return false
                val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
                boxType == "ftyp" || boxType == "styp"
            }
        }.getOrDefault(false)
    }

    fun isFragmentedMp4(file: File): Boolean {
        if (!isMp4(file)) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                var position = 0L
                var boxes = 0
                while (position + 8 <= length && boxes < 10_000) {
                    raf.seek(position)
                    var size = raf.readInt().toLong() and 0xFFFF_FFFFL
                    val typeBytes = ByteArray(4)
                    raf.readFully(typeBytes)
                    val type = String(typeBytes, Charsets.ISO_8859_1)

                    if (size == 1L) {
                        if (position + 16 > length) break
                        size = raf.readLong()
                    }
                    if (size <= 0L || position + size > length) break
                    if (type == "moof") return true

                    position += size
                    boxes++
                }
                false
            }
        }.getOrDefault(false)
    }
}
