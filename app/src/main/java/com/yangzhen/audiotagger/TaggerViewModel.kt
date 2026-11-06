package com.yangzhen.audiotagger

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhen.audiotagger.audio.AudioValidator
import com.yangzhen.audiotagger.audio.MusicTagWriter
import com.yangzhen.audiotagger.data.WriteHistoryStore
import com.yangzhen.audiotagger.util.AudioFileName
import com.yangzhen.audiotagger.util.BilibiliAudioClient
import com.yangzhen.audiotagger.util.CoverProcessor
import com.yangzhen.audiotagger.util.FilenameParser
import com.yangzhen.audiotagger.util.Mp4Container
import com.yangzhen.audiotagger.util.ScannedTrack
import com.yangzhen.audiotagger.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class TaggerViewModel(application: Application) : AndroidViewModel(application) {

    private data class ScanResult(
        val tracks: List<ScannedTrack>,
        val unsupported: Int,
        val formats: Map<String, Int>,
    )

    private data class WriteTrack(
        val file: File,
        val title: String,
        val album: String,
        val cover: ByteArray?,
        val uiTrack: Track,
    )

    private data class WriteGroup(
        val artist: String,
        val album: String,
        val cover: ByteArray?,
        val tracks: List<WriteTrack>,
    )

    private data class FileMove(
        val track: Track,
        val source: File,
        val target: File,
    )

    private data class RenameResult(
        val renamed: Int,
        val unchanged: Int,
        val failed: Int,
        val moves: List<FileMove>,
        val errors: List<String>,
    )

    private val prefs = application.getSharedPreferences("audio_tagger", android.content.Context.MODE_PRIVATE)
    private val coverDir = File(application.filesDir, "covers").apply { mkdirs() }
    private val historyStore = WriteHistoryStore(File(application.filesDir, "write_history.jsonl"))

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var scanJob: Job? = null
    private var writeJob: Job? = null
    private var renameJob: Job? = null

    @Volatile
    private var cancelRequested = false

    init {
        MusicTagWriter.configureLogging()
        _ui.update {
            it.copy(
                permissionGranted = hasAllFilesAccess(),
                lastRootPath = prefs.getString("last_root", null),
                folderMappings = readFolderMappings(),
            )
        }
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyStore.load() }
            _ui.update { it.copy(writeHistory = history) }
        }
    }

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun refreshPermission() {
        _ui.update { it.copy(permissionGranted = hasAllFilesAccess()) }
    }

    fun setRoot(root: File) {
        if (!root.isDirectory) {
            postMessage("文件夹不存在或无法访问")
            return
        }
        prefs.edit().putString("last_root", root.absolutePath).apply()
        _ui.update {
            it.copy(root = root, lastRootPath = root.absolutePath, writeStates = emptyMap())
        }
        scan(root)
    }

    fun rescan() {
        _ui.value.root?.let { scan(it) }
    }

    fun scan(root: File) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    scanning = true,
                    summary = null,
                    progress = null,
                    root = root,
                    message = null,
                )
            }
            val result = withContext(Dispatchers.IO) {
                val scan = scanDirectory(root)
                val groups = buildGroups(root, scan.tracks)
                Triple(scan, groups, groups.count { it.coverSource == CoverSource.AUTO })
            }
            val scan = result.first
            val groups = result.second
            _ui.update {
                it.copy(
                    scanning = false,
                    groups = groups,
                    scannedFiles = scan.tracks.size,
                    unsupportedFiles = scan.unsupported,
                    formatCounts = scan.formats.entries
                        .sortedByDescending { entry -> entry.value }
                        .associate { entry -> entry.key to entry.value },
                    autoCoverCount = result.third,
                    message = if (scan.tracks.isEmpty()) "这个文件夹里没有找到支持的音频文件" else null,
                )
            }
        }
    }

    private fun scanDirectory(root: File): ScanResult {
        val tracks = ArrayList<ScannedTrack>()
        val formats = HashMap<String, Int>()
        var unsupported = 0

        try {
            root.walkTopDown()
                .onEnter { dir ->
                    dir == root || (
                        !dir.name.startsWith(".") &&
                            dir.name !in setOf("Android", "LOST.DIR")
                        )
                }
                .onFail { _, _ -> }
                .filter { it.isFile }
                .forEach { file ->
                    val extension = FilenameParser.extensionOf(file.name)
                    when {
                        FilenameParser.isSupportedAudio(file.name) -> {
                            val parsed = FilenameParser.parse(
                                fileName = file.name,
                                parentFolder = file.parentFile?.name,
                                isRootFolder = file.parentFile == root,
                            )
                            val manualArtist = prefs
                                .getString("track_artist:${file.absolutePath}", null)
                                ?.takeIf { it.isNotBlank() }
                            val folderArtistValue = if (
                                manualArtist == null && parsed.artist == "未知歌手"
                            ) {
                                prefs
                                    .getString("folder_artist:${file.parentFile?.absolutePath}", null)
                                    ?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                            val tagInfo = MusicTagWriter.readBasicTags(file)
                            val tagArtist = if (manualArtist == null && parsed.artist == "未知歌手") {
                                tagInfo?.artist
                                    ?.takeIf { !FilenameParser.isUnknownArtistName(it) }
                            } else {
                                null
                            }
                            val folderArtistApplied = manualArtist == null &&
                                tagArtist == null &&
                                folderArtistValue != null &&
                                parsed.artist == "未知歌手"
                            val finalArtist = manualArtist
                                ?: tagArtist
                                ?: folderArtistValue
                                ?: parsed.artist
                            tracks.add(
                                ScannedTrack(
                                    file = file,
                                    parsed = parsed.copy(artist = finalArtist),
                                    manualArtist = manualArtist != null,
                                    folderArtist = folderArtistApplied,
                                    embeddedAlbum = tagInfo?.album.orEmpty(),
                                )
                            )
                            formats[extension] = (formats[extension] ?: 0) + 1
                        }

                        FilenameParser.isAudioLike(file.name) -> unsupported++
                    }
                }
        } catch (_: Exception) {
            // Keep whatever was readable; the UI reports the final count.
        }

        return ScanResult(
            tracks = FilenameParser.fixReversedNames(tracks),
            unsupported = unsupported,
            formats = formats,
        )
    }

    private fun buildGroups(root: File, tracks: List<ScannedTrack>): List<ArtistGroup> {
        val grouped = LinkedHashMap<String, MutableList<ScannedTrack>>()
        for (track in tracks) {
            val key = FilenameParser.normalize(track.parsed.artist).ifBlank { "未知歌手" }
            grouped.getOrPut(key) { mutableListOf() }.add(track)
        }

        val result = ArrayList<ArtistGroup>(grouped.size)
        for ((key, items) in grouped) {
            val parsedName = items.first().parsed.artist.ifBlank { "未知歌手" }
            val savedName = prefs
                .getString("artist:${root.absolutePath}:$key", null)
                ?.takeIf { it.isNotBlank() }
            val savedAlbum = prefs
                .getString("album:${root.absolutePath}:$key", null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val commonEmbeddedAlbum = items
                .map { it.embeddedAlbum.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            val groupAlbum = savedAlbum ?: commonEmbeddedAlbum ?: DEFAULT_ALBUM
            val group = ArtistGroup(
                key = key,
                initialName = savedName ?: parsedName,
                initialTracks = items.map {
                    val savedTrackAlbum = prefs
                        .getString("track_album:${it.file.absolutePath}", null)
                        ?.trim()
                        ?.takeIf { album -> album.isNotBlank() }
                    Track(
                        file = it.file,
                        parsedArtist = it.parsed.artist,
                        parsedTitle = it.parsed.title,
                        initialManualArtist = it.manualArtist,
                        initialFolderArtist = it.folderArtist,
                        initialAlbum = savedTrackAlbum.orEmpty(),
                        initialEmbeddedAlbum = it.embeddedAlbum,
                        initialAlbumExplicit = savedTrackAlbum != null,
                    )
                },
                initialAlbum = groupAlbum,
                initialAlbumExplicit = savedAlbum != null,
            )

            val persistedCover = File(coverDir, StorageUtils.sha1(key) + ".jpg")
            if (persistedCover.isFile) {
                runCatching { persistedCover.readBytes() }.getOrNull()?.let { bytes ->
                    group.cover = bytes
                    group.coverSource = CoverSource.PICKED
                }
            } else {
                val commonFolder = StorageUtils.commonAncestor(items.map { it.file })
                val allowRootCover = commonFolder == root &&
                    grouped.size == 1 &&
                    !FilenameParser.isGenericFolder(root.name)
                if (commonFolder != null && (commonFolder != root || allowRootCover)) {
                    val coverFile = StorageUtils.findCover(commonFolder)
                    if (coverFile != null) {
                        CoverProcessor.loadFromFile(coverFile).getOrNull()?.let { bytes ->
                            group.cover = bytes
                            group.coverSource = CoverSource.AUTO
                        }
                    }
                }
            }

            group.tracks.forEach(::loadTrackCover)
            result.add(group)
        }

        return sortGroups(result)
    }

    private fun sortGroups(groups: List<ArtistGroup>): List<ArtistGroup> =
        groups.sortedWith(
            compareByDescending<ArtistGroup> { it.tracks.size }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )

    fun assignTrackArtist(track: Track, newArtist: String, rememberFolder: Boolean = false) {
        val cleanName = newArtist.trim()
        if (cleanName.isBlank()) {
            postMessage("歌手名不能为空")
            return
        }
        val groups = _ui.value.groups
        val from = groups.firstOrNull { it.tracks.contains(track) } ?: return
        val targetKey = FilenameParser.normalize(cleanName).ifBlank { "未知歌手" }

        if (targetKey == from.key) {
            from.displayName = cleanName
            saveGroupName(from.key, cleanName)
            track.manualArtist = true
            saveTrackArtist(track.file, cleanName)
            if (rememberFolder) saveFolderArtist(track.file, cleanName)
            postMessage("歌手名已更新：$cleanName")
            return
        }

        moveTrackToArtist(track, from, cleanName, saveMapping = true)
        if (rememberFolder) saveFolderArtist(track.file, cleanName)
    }

    fun clearManualArtist(track: Track) {
        val groups = _ui.value.groups
        val from = groups.firstOrNull { it.tracks.contains(track) } ?: return
        prefs.edit().remove("track_artist:${track.file.absolutePath}").apply()
        track.manualArtist = false
        val parsed = FilenameParser.parse(
            fileName = track.file.name,
            parentFolder = track.file.parentFile?.name,
            isRootFolder = track.file.parentFile == _ui.value.root,
        )
        val folderArtist = if (parsed.artist == "未知歌手") {
            folderArtistFor(track.file)
        } else {
            null
        }
        val restored = folderArtist ?: parsed.artist
        moveTrackToArtist(track, from, restored, saveMapping = false)
        if (folderArtist != null) track.folderArtist = true
        postMessage(
            if (folderArtist != null) {
                "已恢复按文件夹记忆：$restored"
            } else {
                "已恢复按文件名识别：$restored"
            }
        )
    }

    private fun moveTrackToArtist(
        track: Track,
        from: ArtistGroup,
        newArtist: String,
        saveMapping: Boolean,
    ) {
        val cleanName = newArtist.trim().ifBlank { "未知歌手" }
        val targetKey = FilenameParser.normalize(cleanName).ifBlank { "未知歌手" }

        if (targetKey == from.key) {
            from.displayName = cleanName
            saveGroupName(from.key, cleanName)
            if (saveMapping) {
                track.manualArtist = true
                saveTrackArtist(track.file, cleanName)
            }
            return
        }

        val groups = _ui.value.groups
        from.tracks.remove(track)
        val existingTarget = groups.firstOrNull { it.key == targetKey }
        val target = existingTarget ?: ArtistGroup(targetKey, cleanName)
        target.tracks.add(track)

        if (saveMapping) {
            track.manualArtist = true
            saveTrackArtist(track.file, cleanName)
        }

        if (existingTarget == null) {
            loadCoverForNewGroup(target, track.file)
        }

        val updated = groups.toMutableList()
        if (from.tracks.isEmpty()) updated.remove(from)
        if (existingTarget == null) updated.add(target)
        _ui.update { it.copy(groups = sortGroups(updated)) }
        postMessage("已移到歌手：$cleanName")
    }

    private fun loadCoverForNewGroup(group: ArtistGroup, trackFile: File) {
        val persistedCover = File(coverDir, StorageUtils.sha1(group.key) + ".jpg")
        if (persistedCover.isFile) {
            runCatching { persistedCover.readBytes() }.getOrNull()?.let { bytes ->
                group.cover = bytes
                group.coverSource = CoverSource.PICKED
            }
            return
        }

        val parent = trackFile.parentFile ?: return
        val root = _ui.value.root
        if (root != null && parent == root) return
        if (FilenameParser.isGenericFolder(parent.name)) return
        val coverFile = StorageUtils.findCover(parent) ?: return
        CoverProcessor.loadFromFile(coverFile).getOrNull()?.let { bytes ->
            group.cover = bytes
            group.coverSource = CoverSource.AUTO
        }
    }

    private fun trackCoverFile(file: File): File =
        File(coverDir, "track_" + StorageUtils.sha1(file.absolutePath) + ".jpg")

    private fun loadTrackCover(track: Track) {
        val persisted = trackCoverFile(track.file)
        if (!persisted.isFile) return
        runCatching { persisted.readBytes() }.getOrNull()?.let { bytes ->
            track.cover = bytes
            track.coverSource = CoverSource.BILIBILI
        }
    }

    private fun moveTrackCover(oldFile: File, newFile: File) {
        val oldCover = trackCoverFile(oldFile)
        if (!oldCover.isFile) return
        val newCover = trackCoverFile(newFile)
        if (oldCover.absolutePath == newCover.absolutePath) return
        if (newCover.exists()) newCover.delete()
        if (!oldCover.renameTo(newCover)) {
            runCatching {
                oldCover.copyTo(newCover, overwrite = true)
                oldCover.delete()
            }
        }
    }

    private fun moveTrackPrefs(oldFile: File, newFile: File) {
        val editor = prefs.edit()
        listOf("track_artist", "track_album").forEach { prefix ->
            val key = "$prefix:${oldFile.absolutePath}"
            val value = prefs.getString(key, null)
            if (!value.isNullOrBlank()) {
                editor.putString("$prefix:${newFile.absolutePath}", value)
                editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun saveTrackArtist(file: File, artist: String) {
        prefs.edit().putString("track_artist:${file.absolutePath}", artist).apply()
    }

    private fun saveGroupName(key: String, name: String) {
        _ui.value.root?.let { root ->
            prefs.edit().putString("artist:${root.absolutePath}:$key", name).apply()
        }
    }

    private fun readFolderMappings(): Map<String, String> =
        prefs.all.entries.mapNotNull { entry ->
            val key = entry.key
            val value = entry.value as? String
            if (!key.startsWith("folder_artist:") || value.isNullOrBlank()) {
                null
            } else {
                key.removePrefix("folder_artist:") to value
            }
        }.toMap()

    private fun folderArtistFor(file: File): String? =
        prefs
            .getString("folder_artist:${file.parentFile?.absolutePath}", null)
            ?.takeIf { it.isNotBlank() }

    private fun saveFolderArtist(file: File, artist: String) {
        val folderPath = file.parentFile?.absolutePath ?: return
        prefs.edit().putString("folder_artist:$folderPath", artist.trim()).apply()
        _ui.update {
            it.copy(folderMappings = it.folderMappings + (folderPath to artist.trim()))
        }
        applyFolderArtistToUnknown(folderPath, artist.trim())
    }

    fun setFolderArtist(folderPath: String, artist: String) {
        val cleanName = artist.trim()
        if (cleanName.isBlank()) {
            postMessage("歌手名不能为空")
            return
        }
        val folder = File(folderPath)
        if (!folder.isDirectory) {
            postMessage("文件夹不存在或无法访问")
            return
        }
        prefs.edit().putString("folder_artist:$folderPath", cleanName).apply()
        _ui.update {
            it.copy(folderMappings = it.folderMappings + (folderPath to cleanName))
        }
        applyFolderArtistToUnknown(folderPath, cleanName)
        postMessage("已记住：${folder.name} 里识别不到歌手的歌曲归为 $cleanName")
    }

    fun clearFolderArtist(folderPath: String) {
        prefs.edit().remove("folder_artist:$folderPath").apply()
        _ui.update { it.copy(folderMappings = it.folderMappings - folderPath) }
        rescan()
    }

    private fun applyFolderArtistToUnknown(folderPath: String, artist: String) {
        val targetKey = FilenameParser.normalize(artist).ifBlank { "未知歌手" }
        if (targetKey == "未知歌手") return

        val groups = _ui.value.groups
        val targetExists = groups.any { it.key == targetKey }
        val target = groups.firstOrNull { it.key == targetKey } ?: ArtistGroup(targetKey, artist)
        val moving = ArrayList<Pair<ArtistGroup, Track>>()

        for (group in groups) {
            if (group.key == targetKey) continue
            for (track in group.tracks.toList()) {
                if (track.manualArtist) continue
                if (track.file.parentFile?.absolutePath != folderPath) continue
                if (!track.folderArtist && !FilenameParser.isUnknownArtistName(track.parsedArtist)) continue
                moving.add(group to track)
            }
        }

        if (moving.isEmpty()) return

        for ((group, track) in moving) {
            group.tracks.remove(track)
            track.folderArtist = true
            target.tracks.add(track)
        }

        val updated = groups.filter { it.tracks.isNotEmpty() }.toMutableList()
        if (updated.none { it.key == targetKey }) updated.add(target)
        _ui.update { it.copy(groups = sortGroups(updated)) }

        if (!targetExists) {
            loadCoverForNewGroup(target, moving.first().second.file)
        }
    }

    fun renameGroup(key: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            postMessage("歌手名不能为空")
            return
        }
        val group = _ui.value.groups.firstOrNull { it.key == key } ?: return
        group.displayName = cleanName
        _ui.value.root?.let { root ->
            prefs.edit().putString("artist:${root.absolutePath}:$key", cleanName).apply()
        }
        postMessage("已改为：$cleanName")
    }

    fun renameGroupFiles(key: String) {
        val state = _ui.value
        if (state.writing) {
            postMessage("正在写入，请稍后再重命名")
            return
        }
        if (state.scanning) {
            postMessage("正在扫描，请稍后再重命名")
            return
        }
        if (renameJob?.isActive == true) {
            postMessage("正在重命名，请稍候")
            return
        }
        val group = state.groups.firstOrNull { it.key == key } ?: return
        val artist = group.displayName.trim()
        if (FilenameParser.isUnknownArtistName(artist)) {
            postMessage("请先设置歌手名，再按歌手重命名")
            return
        }
        val selectedTracks = group.tracks.filter { it.included }
        if (selectedTracks.isEmpty()) {
            postMessage("没有勾选要改名的歌曲")
            return
        }

        renameJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                renameGroupTracks(group, artist, selectedTracks)
            }

            if (result.moves.isNotEmpty()) {
                val pathChanges = result.moves.associate {
                    it.source.absolutePath to (it.target.absolutePath to it.target.name)
                }
                withContext(Dispatchers.IO) { historyStore.updatePaths(pathChanges) }
                _ui.update { current ->
                    val nextStates = HashMap(current.writeStates)
                    result.moves.forEach { move ->
                        nextStates.remove(move.source.absolutePath)?.let { writeResult ->
                            nextStates[move.target.absolutePath] = writeResult
                        }
                    }
                    current.copy(
                        writeStates = nextStates,
                        writeHistory = current.writeHistory.map { record ->
                            pathChanges[record.path]?.let { (newPath, newName) ->
                                record.copy(path = newPath, fileName = newName)
                            } ?: record
                        },
                    )
                }
                result.moves.forEach { move ->
                    moveTrackPrefs(move.source, move.target)
                    moveTrackCover(move.source, move.target)
                    move.track.file = move.target
                }
                val scanPaths = result.moves.flatMap {
                    listOf(it.source.absolutePath, it.target.absolutePath)
                }
                MediaScannerConnection.scanFile(
                    getApplication(),
                    scanPaths.toTypedArray(),
                    null,
                    null,
                )
            }

            val parts = mutableListOf("重命名 ${result.renamed} 首")
            if (result.unchanged > 0) parts.add("已是目标名称 ${result.unchanged} 首")
            if (result.failed > 0) {
                parts.add("失败 ${result.failed} 首")
                result.errors.firstOrNull()?.let { parts.add(it) }
            }
            postMessage(parts.joinToString("，"))
        }
    }

    private fun renameGroupTracks(
        group: ArtistGroup,
        artist: String,
        selectedTracks: List<Track>,
    ): RenameResult {
        var renamed = 0
        var unchanged = 0
        var failed = 0
        val moves = ArrayList<FileMove>()
        val errors = ArrayList<String>()

        for (track in selectedTracks) {
            val source = track.file
            val directory = source.parentFile
            if (!source.isFile || directory == null) {
                failed++
                errors.add("${source.name}: 文件不存在")
                continue
            }

            val desiredName = AudioFileName.buildFileName(artist, track.title, source.extension)
            val target = uniqueTarget(directory, desiredName, source)
            if (target.absolutePath == source.absolutePath) {
                unchanged++
                continue
            }

            val success = runCatching { source.renameTo(target) }.getOrDefault(false)
            if (success) {
                moves.add(FileMove(track, source, target))
                renamed++
            } else {
                failed++
                errors.add("${source.name}: 无法重命名")
            }
        }

        return RenameResult(renamed, unchanged, failed, moves, errors)
    }

    private fun uniqueTarget(directory: File, fileName: String, source: File): File {
        val first = File(directory, fileName)
        if (!first.exists()) return first
        if (first.absolutePath == source.absolutePath) return source

        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val suffix = if (dot > 0) fileName.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = File(directory, "$base ($index)$suffix")
            if (!candidate.exists()) return candidate
            if (candidate.absolutePath == source.absolutePath) return source
            index++
        }
    }

    fun setCover(groupKey: String, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                CoverProcessor.loadFromUri(getApplication(), uri)
            }
            result.onSuccess { bytes ->
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        File(coverDir, StorageUtils.sha1(groupKey) + ".jpg").writeBytes(bytes)
                    }.isSuccess
                }
                _ui.value.groups.firstOrNull { it.key == groupKey }?.let { group ->
                    group.cover = bytes
                    group.coverSource = CoverSource.PICKED
                }
                postMessage(
                    if (saved) {
                        "封面已更新（${bytes.size / 1024} KB）"
                    } else {
                        "封面已应用，但缓存保存失败"
                    }
                )
            }.onFailure { error ->
                postMessage("封面读取失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun setGroupBilibiliCover(groupKey: String, jpeg: ByteArray) {
        _ui.value.groups.firstOrNull { it.key == groupKey }?.let { group ->
            group.cover = jpeg
            group.coverSource = CoverSource.BILIBILI
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    File(coverDir, StorageUtils.sha1(groupKey) + ".jpg").writeBytes(jpeg)
                }.isSuccess
            }
            postMessage(
                if (saved) {
                    "B站封面已应用到本组（${jpeg.size / 1024} KB）"
                } else {
                    "B站封面已应用，但缓存保存失败"
                }
            )
        }
    }

    fun setTrackCover(track: Track, jpeg: ByteArray) {
        val file = track.file
        track.cover = jpeg
        track.coverSource = CoverSource.BILIBILI
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { trackCoverFile(file).writeBytes(jpeg) }.isSuccess
            }
            postMessage(
                if (saved) {
                    "已设置单曲封面（${jpeg.size / 1024} KB）"
                } else {
                    "单曲封面已应用，但缓存保存失败"
                }
            )
        }
    }

    fun clearTrackCover(track: Track) {
        val file = track.file
        track.cover = null
        track.coverSource = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { trackCoverFile(file).delete() }
            }
            postMessage("已清除单曲封面")
        }
    }

    fun setWriteTitle(enabled: Boolean) {
        _ui.update { it.copy(writeTitle = enabled) }
    }

    fun setWriteAlbum(enabled: Boolean) {
        _ui.update { it.copy(writeAlbum = enabled) }
    }

    fun setSkipIfDone(enabled: Boolean) {
        _ui.update { it.copy(skipIfDone = enabled) }
    }

    fun setGroupAlbum(key: String, album: String) {
        val group = _ui.value.groups.firstOrNull { it.key == key } ?: return
        val cleanAlbum = album.trim().ifBlank { DEFAULT_ALBUM }
        group.album = cleanAlbum
        group.albumExplicit = true
        _ui.value.root?.let { root ->
            prefs.edit()
                .putString("album:${root.absolutePath}:$key", cleanAlbum)
                .apply()
        }
        postMessage("专辑已设为：$cleanAlbum")
    }

    fun setAllIncluded(included: Boolean) {
        val groups = _ui.value.groups
        groups.forEach { group ->
            group.included = included
            group.tracks.forEach { it.included = included }
        }
        val count = if (included) groups.sumOf { it.tracks.size } else 0
        postMessage(if (included) "已全选 $count 首歌" else "已取消全部选择")
    }

    fun setTracksAlbum(tracks: List<Track>, album: String) {
        if (tracks.isEmpty()) {
            postMessage("没有可设置专辑的歌曲")
            return
        }
        val cleanAlbum = album.trim()
        val editor = prefs.edit()
        tracks.forEach { track ->
            track.album = cleanAlbum
            track.albumExplicit = cleanAlbum.isNotBlank()
            val key = "track_album:${track.file.absolutePath}"
            if (cleanAlbum.isBlank()) {
                editor.remove(key)
            } else {
                editor.putString(key, cleanAlbum)
            }
        }
        editor.apply()
        postMessage(
            if (cleanAlbum.isBlank()) {
                "已恢复 ${tracks.size} 首歌使用本组专辑"
            } else {
                "已为 ${tracks.size} 首歌设置专辑：$cleanAlbum"
            }
        )
    }

    suspend fun importBilibiliAudio(request: BilibiliImportRequest): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = _ui.value.root ?: error("请先选择音乐文件夹")
                val artist = request.artist.trim()
                if (artist.isBlank()) error("请填写歌手名")
                val source = BilibiliAudioClient.fetch(request.input).getOrElse { throw it }
                val title = request.title.trim()
                    .ifBlank { source.title.trim() }
                    .ifBlank { "未命名" }
                val album = request.album.trim().ifBlank { DEFAULT_ALBUM }
                val folder = resolveImportFolder(root, artist)
                if (!folder.isDirectory && !folder.mkdirs()) error("无法创建保存文件夹")

                val baseName = AudioFileName.buildFileName(artist, title, "m4a")
                    .removeSuffix(".m4a")
                val temp = uniqueImportTemp(folder, baseName, "m4s")
                try {
                    BilibiliAudioClient.download(source, temp).getOrElse { throw it }
                    if (!Mp4Container.isMp4(temp)) error("下载到的内容不是可识别的音频容器")
                    val outcome = MusicTagWriter.write(
                        file = temp,
                        artist = artist,
                        album = album,
                        title = title,
                        coverJpeg = source.coverJpeg,
                        writeTitle = true,
                        writeAlbum = true,
                        skipIfDone = false,
                        validator = AudioValidator::validate,
                        audioOnly = true,
                    )
                    val finalFile = when (outcome.status) {
                        MusicTagWriter.Status.SUCCESS -> outcome.finalFile
                            ?: error("音频已处理，但没有得到最终文件")
                        MusicTagWriter.Status.FAILED -> error(outcome.message ?: "音频写入失败")
                        MusicTagWriter.Status.SKIPPED -> error("音频未写入")
                    }
                    scan(root)
                    finalFile
                } finally {
                    if (temp.exists()) temp.delete()
                }
            }
        }

    private fun resolveImportFolder(root: File, artist: String): File {
        val safeArtist = AudioFileName.sanitizePart(artist).ifBlank { "未知歌手" }
        val direct = File(root, safeArtist)
        if (direct.isDirectory) return direct

        val key = FilenameParser.normalize(artist)
        val group = _ui.value.groups.firstOrNull {
            it.key == key || FilenameParser.normalize(it.displayName) == key
        }
        val existingFolder = group?.tracks
            ?.mapNotNull { it.file.parentFile }
            ?.groupingBy { it.absolutePath }
            ?.eachCount()
            ?.maxByOrNull { it.value }
            ?.key
            ?.let(::File)
        return existingFolder?.takeIf { it.isDirectory && it != root } ?: root
    }

    private fun uniqueImportTemp(directory: File, baseName: String, extension: String): File {
        var index = 1
        var candidate = File(directory, "$baseName.$extension")
        while (candidate.exists()) {
            index++
            candidate = File(directory, "$baseName ($index).$extension")
        }
        return candidate
    }

    fun openHistory() {
        _ui.update { it.copy(historyVisible = true) }
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyStore.load() }
            _ui.update { it.copy(writeHistory = history) }
        }
    }

    fun closeHistory() {
        _ui.update { it.copy(historyVisible = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.clear() }
            _ui.update { it.copy(writeHistory = emptyList()) }
            postMessage("写入记录已清空")
        }
    }

    fun writeAll() {
        val state = _ui.value
        if (state.writing) return
        if (renameJob?.isActive == true) {
            postMessage("正在重命名，请稍候")
            return
        }

        val writeGroups = state.groups
            .filter { it.includedCount > 0 }
            .map { group ->
                WriteGroup(
                    artist = group.displayName.trim(),
                    album = group.album.trim(),
                    cover = group.cover,
                    tracks = group.tracks
                        .filter { it.included }
                        .map {
                            WriteTrack(
                                file = it.file,
                                title = it.title.trim(),
                                album = it.albumFor(group.album, group.albumExplicit),
                                cover = it.cover,
                                uiTrack = it,
                            )
                        },
                )
            }

        val total = writeGroups.sumOf { it.tracks.size }
        if (total == 0) {
            postMessage("没有可写入的歌曲")
            return
        }

        val writeTitle = state.writeTitle
        val writeAlbum = state.writeAlbum
        val skipIfDone = state.skipIfDone
        cancelRequested = false

        writeJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    writing = true,
                    stopping = false,
                    summary = null,
                    progress = WriteProgress(0, total, "", 0, 0, 0),
                )
            }

            var success = 0
            var skipped = 0
            var failed = 0
            var renamed = 0
            var current = 0
            val errors = ArrayList<String>()
            val changedPaths = ArrayList<String>()
            val renamedTracks = ArrayList<Pair<Track, File>>()

            withContext(Dispatchers.IO) {
                fun recordOutcome(
                    group: WriteGroup,
                    track: WriteTrack,
                    outcome: MusicTagWriter.Outcome,
                ): File {
                    val resultFile = outcome.finalFile ?: track.file
                    val writeState = when (outcome.status) {
                        MusicTagWriter.Status.SUCCESS -> TrackWriteState.SUCCESS
                        MusicTagWriter.Status.SKIPPED -> TrackWriteState.SKIPPED
                        MusicTagWriter.Status.FAILED -> TrackWriteState.FAILED
                    }
                    val now = System.currentTimeMillis()
                    historyStore.append(
                        WriteRecord(
                            finishedAt = now,
                            path = resultFile.absolutePath,
                            fileName = resultFile.name,
                            artist = group.artist,
                            album = track.album,
                            state = writeState,
                            message = outcome.message,
                        )
                    )
                    _ui.update {
                        it.copy(
                            writeStates = it.writeStates + (
                                resultFile.absolutePath to TrackWriteResult(
                                    state = writeState,
                                    message = outcome.message,
                                    finishedAt = now,
                                )
                                )
                        )
                    }
                    if (writeState == TrackWriteState.SUCCESS &&
                        resultFile.absolutePath != track.file.absolutePath
                    ) {
                        renamedTracks.add(track.uiTrack to resultFile)
                    }
                    return resultFile
                }

                outer@ for (group in writeGroups) {
                    if (cancelRequested) break

                    if (group.artist.isBlank()) {
                        for (track in group.tracks) {
                            if (cancelRequested) break@outer
                            val outcome = MusicTagWriter.Outcome(
                                MusicTagWriter.Status.FAILED, "歌手名为空"
                            )
                            val resultFile = recordOutcome(group, track, outcome)
                            failed++
                            current++
                            errors.add("${resultFile.name}: ${outcome.message}")
                            _ui.update {
                                it.copy(
                                    progress = WriteProgress(
                                        current, total, resultFile.name, success, skipped, failed
                                    )
                                )
                            }
                        }
                        continue
                    }

                    for (track in group.tracks) {
                        if (cancelRequested) break@outer
                        val outcome = MusicTagWriter.write(
                            file = track.file,
                            artist = group.artist,
                            album = track.album,
                            title = track.title,
                            coverJpeg = track.cover ?: group.cover,
                            writeTitle = writeTitle,
                            writeAlbum = writeAlbum,
                            skipIfDone = skipIfDone,
                            validator = AudioValidator::validate,
                        )
                        val resultFile = recordOutcome(group, track, outcome)
                        when (outcome.status) {
                            MusicTagWriter.Status.SUCCESS -> {
                                success++
                                changedPaths.add(resultFile.absolutePath)
                            }

                            MusicTagWriter.Status.SKIPPED -> skipped++

                            MusicTagWriter.Status.FAILED -> {
                                failed++
                                errors.add("${resultFile.name}: ${outcome.message ?: "写入失败"}")
                            }
                        }
                        current++
                        _ui.update {
                            it.copy(
                                progress = WriteProgress(
                                    current, total, resultFile.name, success, skipped, failed
                                )
                            )
                        }
                    }
                }

                if (changedPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        getApplication(),
                        changedPaths.toTypedArray(),
                        null,
                        null,
                    )
                }

                val history = historyStore.load()
                _ui.update { it.copy(writeHistory = history) }
            }

            renamed = renamedTracks.size
            renamedTracks.forEach { (track, newFile) ->
                val oldFile = track.file
                moveTrackPrefs(oldFile, newFile)
                moveTrackCover(oldFile, newFile)
                track.file = newFile
            }

            _ui.update {
                it.copy(
                    writing = false,
                    stopping = false,
                    progress = null,
                    summary = WriteSummary(
                        total = total,
                        success = success,
                        skipped = skipped,
                        failed = failed,
                        renamed = renamed,
                        cancelled = cancelRequested,
                        errors = errors,
                    ),
                )
            }
        }
    }

    fun cancelWrite() {
        if (!_ui.value.writing) return
        cancelRequested = true
        _ui.update { it.copy(stopping = true) }
    }

    fun dismissSummary() {
        _ui.update { it.copy(summary = null) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun postMessage(message: String) {
        _ui.update { it.copy(message = message) }
    }
}



