package com.yangzhen.audiotagger

import com.yangzhen.audiotagger.util.BilibiliLinkParser
import com.yangzhen.audiotagger.util.BilibiliVideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BilibiliLinkParserTest {

    @Test
    fun parsesRawBvId() {
        assertEquals(
            BilibiliVideoRef.Bv("BV1xx411c7mD"),
            BilibiliLinkParser.parse("BV1xx411c7mD"),
        )
    }

    @Test
    fun parsesBvFromVideoUrlAndShareText() {
        assertEquals(
            BilibiliVideoRef.Bv("BV1xx411c7mD"),
            BilibiliLinkParser.parse(
                "https://www.bilibili.com/video/BV1xx411c7mD/?spm_id_from=333.999&p=2"
            ),
        )
        assertEquals(
            BilibiliVideoRef.Bv("BV1xx411c7mD"),
            BilibiliLinkParser.parse(
                "【测试视频】 https://www.bilibili.com/video/BV1xx411c7mD 分享自 B 站"
            ),
        )
    }

    @Test
    fun normalizesLowercaseBvPrefix() {
        assertEquals(
            BilibiliVideoRef.Bv("BV1xx411c7mD"),
            BilibiliLinkParser.parse("bv1xx411c7mD"),
        )
    }

    @Test
    fun parsesAvId() {
        assertEquals(
            BilibiliVideoRef.Av(170001L),
            BilibiliLinkParser.parse("https://www.bilibili.com/video/av170001/"),
        )
        assertEquals(BilibiliVideoRef.Av(170001L), BilibiliLinkParser.parse("av170001"))
    }

    @Test
    fun recognizesShortLinks() {
        assertEquals(
            BilibiliVideoRef.ShortLink("https://b23.tv/abc123"),
            BilibiliLinkParser.parse("https://b23.tv/abc123"),
        )
        assertEquals(
            BilibiliVideoRef.ShortLink("https://b23.tv/abc123"),
            BilibiliLinkParser.parse("http://b23.tv/abc123"),
        )
        assertEquals(
            BilibiliVideoRef.ShortLink("https://bili2233.cn/abc123"),
            BilibiliLinkParser.parse("bili2233.cn/abc123"),
        )
    }

    @Test
    fun rejectsUnrelatedText() {
        assertNull(BilibiliLinkParser.parse(""))
        assertNull(BilibiliLinkParser.parse("这不是视频链接"))
        assertNull(BilibiliLinkParser.parse("https://example.com/video/123"))
    }
}
