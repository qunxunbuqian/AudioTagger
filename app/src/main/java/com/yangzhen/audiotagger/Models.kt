package com.yangzhen.audiotagger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File

const val DEFAULT_ALBUM = "投稿"

class Track(
    file: File,
    val parsedArtist: String,
    val parsedTitle: String,
    initialManualArtist: Boolean = false,
    initialFolderArtist: Boolean = false,
    initialAlbum: String = "",
    initialEmbeddedAlbum: String = "",
    initialAlbumExplicit: Boolean = false,
) {
    var file by mutableStateOf(file)
    var title by mutableStateOf(parsedTitle)
    var album by mutableStateOf(initialAlbum)
    var albumExplicit by mutableStateOf(initialAlbumExplicit)
    var embeddedAlbum by mutableStateOf(initialEmbeddedAlbum)
    var included by mutableStateOf(true)
    var manualArtist by mutableStateOf(initialManualArtist)
    var folderArtist by mutableStateOf(initialFolderArtist)
    var cover by mutableStateOf<ByteArray?>(null)
    var coverSource by mutableStateOf<CoverSource?>(null)

    fun albumFor(groupAlbum: String, groupAlbumExplicit: Boolean): String = when {
        albumExplicit && album.isNotBlank() -> album.trim()
        groupAlbumExplicit -> groupAlbum.trim().ifBlank { DEFAULT_ALBUM }
        embeddedAlbum.isNotBlank() -> embeddedAlbum.trim()
        else -> groupAlbum.trim().ifBlank { DEFAULT_ALBUM }
    }
}

enum class TrackWriteState { SUCCESS, SKIPPED, FAILED }

data class TrackWriteResult(
    val state: TrackWriteState,
    val message: String?,
    val finishedAt: Long,
)

data class WriteRecord(
    val finishedAt: Long,
    val path: String,
    val fileName: String,
    val artist: String,
    val album: String,
    val state: TrackWriteState,
    val message: String?,
)

enum class CoverSource { AUTO, PICKED, BILIBILI }

class ArtistGroup(
    val key: String,
    initialName: String,
    initialTracks: List<Track> = emptyList(),
    initialAlbum: String = DEFAULT_ALBUM,
    initialAlbumExplicit: Boolean = false,
) {
    val tracks: SnapshotStateList<Track> = mutableStateListOf<Track>().apply { addAll(initialTracks) }

    var displayName by mutableStateOf(initialName)
    var album by mutableStateOf(initialAlbum)
    var albumExplicit by mutableStateOf(initialAlbumExplicit)
    var cover by mutableStateOf<ByteArray?>(null)
    var coverSource by mutableStateOf<CoverSource?>(null)
    var included by mutableStateOf(true)

    val includedCount: Int
        get() = tracks.count { it.included }
}

data class WriteProgress(
    val current: Int,
    val total: Int,
    val currentName: String,
    val success: Int,
    val skipped: Int,
    val failed: Int,
)

data class WriteSummary(
    val total: Int,
    val success: Int,
    val skipped: Int,
    val failed: Int,
    val renamed: Int,
    val cancelled: Boolean,
    val errors: List<String>,
)

data class BilibiliImportRequest(
    val input: String,
    val artist: String,
    val title: String,
    val album: String,
)

data class UiState(
    val permissionGranted: Boolean = false,
    val root: File? = null,
    val groups: List<ArtistGroup> = emptyList(),
    val scannedFiles: Int = 0,
    val unsupportedFiles: Int = 0,
    val formatCounts: Map<String, Int> = emptyMap(),
    val autoCoverCount: Int = 0,
    val scanning: Boolean = false,
    val writing: Boolean = false,
    val stopping: Boolean = false,
    val progress: WriteProgress? = null,
    val summary: WriteSummary? = null,
    val writeTitle: Boolean = true,
    val writeAlbum: Boolean = true,
    val skipIfDone: Boolean = false,
    val message: String? = null,
    val lastRootPath: String? = null,
    val folderMappings: Map<String, String> = emptyMap(),
    val writeStates: Map<String, TrackWriteResult> = emptyMap(),
    val writeHistory: List<WriteRecord> = emptyList(),
    val historyVisible: Boolean = false,
) {
    val groupCount: Int get() = groups.size
    val totalTracks: Int get() = groups.sumOf { it.tracks.size }
    val totalIncluded: Int get() = groups.sumOf { it.includedCount }
}
