package com.yangzhen.audiotagger

import com.yangzhen.audiotagger.util.AudioFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileNameTest {

    @Test
    fun buildsArtistTitleName() {
        assertEquals(
            "塔克Tako - Bloodshot.mp3",
            AudioFileName.buildFileName("塔克Tako", "Bloodshot", "mp3"),
        )
        assertEquals(
            "林俊杰 - 黑夜问白天.m4a",
            AudioFileName.buildFileName("林俊杰", "黑夜问白天", ".m4a"),
        )
    }

    @Test
    fun replacesIllegalFileNameCharacters() {
        assertEquals(
            "A／B - What？：＊.flac",
            AudioFileName.buildFileName("A/B", "What?:*", "flac"),
        )
    }

    @Test
    fun removesControlCharactersAndTrailingDots() {
        assertEquals(
            "Artist - Title.mp3",
            AudioFileName.buildFileName("Artist ", "Title.\n", "mp3"),
        )
        assertEquals(
            "A B - C D.mp3",
            AudioFileName.buildFileName("A\tB", "C\r\nD", "mp3"),
        )
    }

    @Test
    fun fallsBackWhenArtistOrTitleIsBlank() {
        assertEquals(
            "未知歌手 - 未命名.mp3",
            AudioFileName.buildFileName("  ", "  ", "mp3"),
        )
    }

    @Test
    fun keepsLongNamesWithinByteLimit() {
        val name = AudioFileName.buildFileName(
            "很长的歌手名字".repeat(20),
            "很长的歌曲名字".repeat(50),
            "mp3",
        )
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue(name.endsWith(".mp3"))
        assertTrue(name.contains(" - "))
    }
}
