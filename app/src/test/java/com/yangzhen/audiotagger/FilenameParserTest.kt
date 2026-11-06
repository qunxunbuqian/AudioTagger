package com.yangzhen.audiotagger

import com.yangzhen.audiotagger.util.FilenameParser
import com.yangzhen.audiotagger.util.ParsedName
import com.yangzhen.audiotagger.util.ScannedTrack
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FilenameParserTest {

    private fun parse(name: String, parent: String = "Music", isRoot: Boolean = true): ParsedName {
        return FilenameParser.parse(name, parent, isRoot)
    }

    @Test
    fun parsesStandardNames() {
        assertEquals(ParsedName("林俊杰", "黑夜问白天"), parse("林俊杰 - 黑夜问白天 [mqms2].mp3"))
        assertEquals(ParsedName("薛之谦", "高尚"), parse("薛之谦 - 高尚.mp3"))
        assertEquals(ParsedName("4円", "アイロニ (反语)"), parse("4円 - アイロニ (反语).mp3"))
        assertEquals(ParsedName("Kina、Snw", "Get You The Moon"), parse("Kina、Snw - Get You The Moon.mp3"))
        assertEquals(ParsedName("ＴＲＡ＄Ｈ", "WAITING"), parse("ＴＲＡ＄Ｈ - WAITING.mp3"))
        assertEquals(
            ParsedName("Lover (Remix)", "Taylor Swift _ Shawn Mendes"),
            parse("Lover (Remix) - Taylor Swift _ Shawn Mendes.mp3"),
        )
    }

    @Test
    fun stripsJunkAndRepeatedExtensions() {
        assertEquals("AURORA - Cure For Me", FilenameParser.cleanName("AURORA - Cure For Me.mp3.mp3"))
        assertEquals(ParsedName("AURORA", "Cure For Me"), parse("AURORA - Cure For Me.mp3.mp3"))
        assertEquals(ParsedName("林俊杰", "裂缝中的阳光"), parse("林俊杰 - 裂缝中的阳光.MP3"))
        assertEquals(ParsedName("胡杨林", "多情种"), parse("胡杨林 - 多情种 [mqms2].mp3"))
    }

    @Test
    fun parsesAndRecognizesWavFiles() {
        assertEquals(ParsedName("塔克Tako", "Bloodshot"), parse("塔克Tako - Bloodshot.wav"))
        assertEquals(true, FilenameParser.isSupportedAudio("塔克Tako - Bloodshot.WAV"))
    }

    @Test
    fun handlesNoSpaceAndHyphenatedArtists() {
        assertEquals(ParsedName("林俊杰", "慢慢喜欢你"), parse("林俊杰-慢慢喜欢你.mp3"))
        assertEquals(ParsedName("A-Lin", "给我一个理由忘记"), parse("A-Lin-给我一个理由忘记.mp3"))
        assertEquals(
            ParsedName("Bomb比尔", "1201牵手-比尔的歌"),
            parse("Bomb比尔 - 1201牵手-比尔的歌.mp3"),
        )
    }

    @Test
    fun fallsBackToArtistFolder() {
        assertEquals(ParsedName("周杰伦", "晴天"), parse("01. 晴天.mp3", parent = "周杰伦", isRoot = false))
        assertEquals(ParsedName("未知歌手", "晴天"), parse("晴天.mp3", parent = "Music", isRoot = true))
    }

    @Test
    fun fixesReversedDownloadNames() {
        val items = listOf(
            ScannedTrack(File("林俊杰 - 距离.mp3"), ParsedName("林俊杰", "距离")),
            ScannedTrack(File("林俊杰 - 幸存者.mp3"), ParsedName("林俊杰", "幸存者")),
            ScannedTrack(File("一定会 - 林俊杰.mp3"), ParsedName("一定会", "林俊杰")),
        )
        val fixed = FilenameParser.fixReversedNames(items)
        assertEquals(ParsedName("林俊杰", "一定会"), fixed[2].parsed)
    }

    @Test
    fun doesNotSwapFolderArtistTracks() {
        val items = listOf(
            ScannedTrack(File("林俊杰 - 距离.mp3"), ParsedName("林俊杰", "距离")),
            ScannedTrack(File("林俊杰 - 幸存者.mp3"), ParsedName("林俊杰", "幸存者")),
            ScannedTrack(
                File("一定会 - 林俊杰.mp3"),
                ParsedName("一定会", "林俊杰"),
                folderArtist = true,
            ),
        )
        val fixed = FilenameParser.fixReversedNames(items)
        assertEquals(ParsedName("一定会", "林俊杰"), fixed[2].parsed)
    }

    @Test
    fun recognizesUnknownArtistTags() {
        assertEquals(true, FilenameParser.isUnknownArtistName(null))
        assertEquals(true, FilenameParser.isUnknownArtistName(" "))
        assertEquals(true, FilenameParser.isUnknownArtistName("Unknown Artist"))
        assertEquals(true, FilenameParser.isUnknownArtistName("未知歌手"))
        assertEquals(false, FilenameParser.isUnknownArtistName("林俊杰"))
    }
}

