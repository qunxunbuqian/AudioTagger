package com.yangzhen.audiotagger.audio

import org.mp4parser.PropertyBoxParserImpl
import org.mp4parser.muxer.FileRandomAccessSourceImpl
import org.mp4parser.muxer.Movie
import org.mp4parser.muxer.builder.DefaultMp4Builder
import org.mp4parser.muxer.container.mp4.MovieCreator
import org.mp4parser.boxes.iso14496.part12.MovieBox
import org.mp4parser.boxes.iso14496.part12.TrackBox
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Properties

object Mp4Remuxer {

    fun remux(source: File, target: File, audioOnly: Boolean = false) {
        ensureBoxMapReady()
        FileInputStream(source).use { input ->
            FileRandomAccessSourceImpl(RandomAccessFile(source, "r")).use { sourceData ->
                val movie = MovieCreator.build(input.channel, sourceData, source.absolutePath)
                require(movie.tracks.isNotEmpty()) { "没有找到音轨" }
                val selectedTracks = if (audioOnly) {
                    movie.tracks.filter { track -> track.handler == "soun" }
                } else {
                    movie.tracks
                }
                require(selectedTracks.isNotEmpty()) { "没有找到音频轨" }
                val selectedMovie = if (audioOnly) Movie(selectedTracks) else movie
                val container = DefaultMp4Builder().build(selectedMovie)
                fixDurations(selectedMovie, container)
                FileOutputStream(target).use { output ->
                    container.writeContainer(output.channel)
                }
            }
        }
        require(target.isFile && target.length() > 0L) { "MP4 修复后文件为空" }
    }

    private fun ensureBoxMapReady() {
        if (PropertyBoxParserImpl.BOX_MAP_CACHE != null) return
        synchronized(PropertyBoxParserImpl::class.java) {
            if (PropertyBoxParserImpl.BOX_MAP_CACHE != null) return
            val loader = Mp4Remuxer::class.java.classLoader
                ?: error("找不到 App 类加载器")
            val input = loader.getResourceAsStream("isoparser2-default.properties")
                ?: error("找不到 isoparser2-default.properties，无法解析 MP4")
            val properties = Properties()
            input.use { properties.load(it) }
            PropertyBoxParserImpl.BOX_MAP_CACHE = properties
        }
    }

    private fun fixDurations(
        movie: org.mp4parser.muxer.Movie,
        container: org.mp4parser.Container,
    ) {
        val movieBox = container.boxes.filterIsInstance<MovieBox>().firstOrNull() ?: return
        val movieHeader = movieBox.movieHeaderBox ?: return
        val seconds = movie.tracks.maxOfOrNull { track ->
            val timescale = track.trackMetaData.timescale.takeIf { it > 0L } ?: 1L
            track.duration.toDouble() / timescale
        } ?: 0.0

        val movieTimescale = movieHeader.timescale.takeIf { it > 0L } ?: 1_000L
        movieHeader.timescale = movieTimescale
        val duration = (seconds * movieTimescale).toLong()
        movieHeader.duration = duration
        movieBox.boxes.filterIsInstance<TrackBox>().forEach { trackBox ->
            trackBox.trackHeaderBox?.duration = duration
        }
    }
}
