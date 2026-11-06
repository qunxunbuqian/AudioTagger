package com.yangzhen.audiotagger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yangzhen.audiotagger.ArtistGroup
import com.yangzhen.audiotagger.BilibiliImportRequest
import com.yangzhen.audiotagger.CoverSource
import com.yangzhen.audiotagger.DEFAULT_ALBUM
import com.yangzhen.audiotagger.Track
import com.yangzhen.audiotagger.TrackWriteResult
import com.yangzhen.audiotagger.TrackWriteState
import com.yangzhen.audiotagger.UiState
import com.yangzhen.audiotagger.WriteRecord
import com.yangzhen.audiotagger.util.AudioFileName
import com.yangzhen.audiotagger.util.BilibiliCover
import com.yangzhen.audiotagger.util.BilibiliCoverClient
import com.yangzhen.audiotagger.util.FilenameParser
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    ui: UiState,
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
    onPickCover: (String) -> Unit,
    onSetGroupBilibiliCover: (String, ByteArray) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onRenameGroupFiles: (String) -> Unit,
    onAssignArtist: (Track, String, Boolean) -> Unit,
    onClearManualArtist: (Track) -> Unit,
    onSetTrackCover: (Track, ByteArray) -> Unit,
    onClearTrackCover: (Track) -> Unit,
    onSetFolderArtist: (String, String) -> Unit,
    onClearFolderArtist: (String) -> Unit,
    onSetGroupAlbum: (String, String) -> Unit,
    onSetAllIncluded: (Boolean) -> Unit,
    onSetTracksAlbum: (List<Track>, String) -> Unit,
    onWrite: () -> Unit,
    onToggleWriteTitle: (Boolean) -> Unit,
    onToggleWriteAlbum: (Boolean) -> Unit,
    onToggleSkip: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onCloseHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onImportBilibiliAudio: suspend (BilibiliImportRequest) -> Result<File>,
) {
    var songGroup by remember { mutableStateOf<ArtistGroup?>(null) }
    var renameTarget by remember { mutableStateOf<ArtistGroup?>(null) }
    var fileRenameTarget by remember { mutableStateOf<ArtistGroup?>(null) }
    var groupBilibiliTarget by remember { mutableStateOf<ArtistGroup?>(null) }
    var albumTarget by remember { mutableStateOf<ArtistGroup?>(null) }
    var showBatchAlbum by remember { mutableStateOf(false) }
    var showImportAudio by remember { mutableStateOf(false) }
    var showFolderMemory by remember { mutableStateOf(false) }
    val writeStates = remember(ui.writeStates, ui.writeHistory) {
        mergeWriteStates(ui.writeStates, ui.writeHistory)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("音频标签", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${ui.totalIncluded} 首 · ${ui.groupCount} 位歌手",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { showImportAudio = true }) {
                    Icon(Icons.Default.Download, contentDescription = "新增 B 站音频")
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "写入记录")
                }
                IconButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                }
                IconButton(onClick = onChangeFolder) {
                    Icon(Icons.Default.Folder, contentDescription = "更换文件夹")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item(key = "header") {
                HeaderCard(ui = ui, onChangeFolder = onChangeFolder)
            }
            item(key = "options") {
                OptionsCard(
                    ui = ui,
                    onToggleWriteTitle = onToggleWriteTitle,
                    onToggleWriteAlbum = onToggleWriteAlbum,
                    onToggleSkip = onToggleSkip,
                    onManageFolders = { showFolderMemory = true },
                    onOpenHistory = onOpenHistory,
                )
            }
            item(key = "batch") {
                BatchSelectionCard(
                    ui = ui,
                    onSelectAll = { onSetAllIncluded(true) },
                    onSelectNone = { onSetAllIncluded(false) },
                    onOpenAlbumFilter = { showBatchAlbum = true },
                )
            }
            if (ui.groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(onChangeFolder = onChangeFolder)
                }
            }
            items(ui.groups, key = { it.key }) { group ->
                GroupCard(
                    group = group,
                    onPickCover = { onPickCover(group.key) },
                    onPickBilibiliCover = { groupBilibiliTarget = group },
                    onRename = { renameTarget = group },
                    onRenameFiles = { fileRenameTarget = group },
                    onEditAlbum = { albumTarget = group },
                    onShowSongs = { songGroup = group },
                    onManageFolders = { showFolderMemory = true },
                    writeStates = writeStates,
                )
            }
        }

        BottomWriteBar(ui = ui, onWrite = onWrite)
    }

    songGroup?.let { group ->
        SongListDialog(
            group = group,
            onAssignArtist = onAssignArtist,
            onClearManualArtist = onClearManualArtist,
            onSetTrackCover = onSetTrackCover,
            onClearTrackCover = onClearTrackCover,
            onSetTracksAlbum = onSetTracksAlbum,
            writeStates = writeStates,
            onDismiss = { songGroup = null },
        )
    }

    renameTarget?.let { group ->
        ArtistNameDialog(
            group = group,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRenameGroup(group.key, name)
                renameTarget = null
            },
        )
    }

    fileRenameTarget?.let { group ->
        FileRenameDialog(
            group = group,
            onDismiss = { fileRenameTarget = null },
            onConfirm = {
                onRenameGroupFiles(group.key)
                fileRenameTarget = null
            },
        )
    }

    groupBilibiliTarget?.let { group ->
        BilibiliCoverDialog(
            onDismiss = { groupBilibiliTarget = null },
            onUse = { cover ->
                onSetGroupBilibiliCover(group.key, cover.jpeg)
                groupBilibiliTarget = null
            },
        )
    }

    albumTarget?.let { group ->
        AlbumNameDialog(
            group = group,
            onDismiss = { albumTarget = null },
            onConfirm = { album ->
                onSetGroupAlbum(group.key, album)
                albumTarget = null
            },
        )
    }

    if (showBatchAlbum) {
        BatchAlbumDialog(
            ui = ui,
            onSetAllIncluded = onSetAllIncluded,
            onSetTracksAlbum = onSetTracksAlbum,
            onDismiss = { showBatchAlbum = false },
        )
    }

    if (showImportAudio) {
        BilibiliImportDialog(
            root = ui.root,
            onImport = onImportBilibiliAudio,
            onDismiss = { showImportAudio = false },
        )
    }

    if (showFolderMemory) {
        FolderMemoryDialog(
            ui = ui,
            onSave = onSetFolderArtist,
            onClear = onClearFolderArtist,
            onDismiss = { showFolderMemory = false },
        )
    }

    if (ui.historyVisible) {
        WriteHistoryDialog(
            records = ui.writeHistory,
            onDismiss = onCloseHistory,
            onClear = onClearHistory,
        )
    }
}

private fun mergeWriteStates(
    session: Map<String, TrackWriteResult>,
    history: List<WriteRecord>,
): Map<String, TrackWriteResult> {
    if (history.isEmpty()) return session

    val latestByPath = HashMap<String, WriteRecord>()
    history.forEach { record ->
        val current = latestByPath[record.path]
        if (current == null || record.finishedAt >= current.finishedAt) {
            latestByPath[record.path] = record
        }
    }

    val merged = HashMap<String, TrackWriteResult>(session.size + latestByPath.size)
    latestByPath.forEach { (path, record) ->
        if (path !in session) {
            merged[path] = TrackWriteResult(record.state, record.message, record.finishedAt)
        }
    }
    merged.putAll(session)
    return merged
}

private enum class WriteFilter(val label: String) {
    ALL("全部"),
    UNWRITTEN("未写入"),
    WRITTEN("已写入"),
}

private fun isWritten(result: TrackWriteResult?): Boolean =
    result?.state == TrackWriteState.SUCCESS || result?.state == TrackWriteState.SKIPPED

@Composable
private fun HeaderCard(ui: UiState, onChangeFolder: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onChangeFolder)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "音乐文件夹",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ui.root?.absolutePath ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("${ui.scannedFiles} 首")
                StatPill("${ui.groupCount} 位歌手")
                StatPill("${ui.formatCounts.size} 种格式")
            }

            if (ui.formatCounts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ui.formatCounts.entries.joinToString(" · ") {
                        "${it.key.uppercase(Locale.ROOT)} ${it.value}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ui.autoCoverCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "已自动匹配 ${ui.autoCoverCount} 张文件夹封面",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (ui.unsupportedFiles > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "另有 ${ui.unsupportedFiles} 个音频格式暂不支持",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun OptionsCard(
    ui: UiState,
    onToggleWriteTitle: (Boolean) -> Unit,
    onToggleWriteAlbum: (Boolean) -> Unit,
    onToggleSkip: (Boolean) -> Unit,
    onManageFolders: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "写入选项",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            OptionSwitch(
                title = "同时写入歌名",
                subtitle = "从“歌手 - 歌名”文件名提取，已去掉 [mqms2] 等后缀",
                checked = ui.writeTitle,
                onCheckedChange = onToggleWriteTitle,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            OptionSwitch(
                title = "同时写入专辑",
                subtitle = "默认“投稿”；歌手卡片或单曲里可以改写",
                checked = ui.writeAlbum,
                onCheckedChange = onToggleWriteAlbum,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            OptionSwitch(
                title = "跳过已处理",
                subtitle = "歌手、歌名和封面都已存在时跳过，适合重复运行",
                checked = ui.skipIfDone,
                onCheckedChange = onToggleSkip,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "文件夹歌手记忆",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "识别不到歌手时，按文件夹自动归属；以后新增歌曲也会生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onManageFolders) {
                    Text(if (ui.folderMappings.isEmpty()) "设置" else "管理")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "写入记录",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (ui.writeHistory.isEmpty()) {
                            "还没有写入记录"
                        } else {
                            "最近 ${ui.writeHistory.size} 条，失败 / 跳过 / 成功分段显示"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenHistory) {
                    Text("查看")
                }
            }
        }
    }
}

@Composable
private fun BatchSelectionCard(
    ui: UiState,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onOpenAlbumFilter: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "批量选择与专辑",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "已选 ${ui.totalIncluded} / ${ui.totalTracks} 首。可以按专辑筛选，再只处理筛选结果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onSelectAll) {
                    Text("全选所有")
                }
                TextButton(onClick = onSelectNone) {
                    Text("全不选")
                }
                TextButton(onClick = onOpenAlbumFilter) {
                    Text("按专辑筛选")
                }
            }
        }
    }
}

private data class AlbumFilterTrack(
    val group: ArtistGroup,
    val track: Track,
) {
    val album: String get() = track.albumFor(group.album, group.albumExplicit)
}

@Composable
private fun BatchAlbumDialog(
    ui: UiState,
    onSetAllIncluded: (Boolean) -> Unit,
    onSetTracksAlbum: (List<Track>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = ui.groups.flatMap { group ->
        group.tracks.map { track -> AlbumFilterTrack(group, track) }
    }
    val albumCounts = entries.groupingBy { it.album }.eachCount()
    val albumOptions = albumCounts.keys.sortedWith(
        compareBy<String> { it.isBlank() }.thenBy { it.lowercase(Locale.ROOT) }
    )
    var filterAlbum by remember { mutableStateOf<String?>(null) }
    var newAlbum by remember { mutableStateOf(DEFAULT_ALBUM) }
    val filtered = if (filterAlbum == null) {
        entries
    } else {
        entries.filter { it.album == filterAlbum }
    }
    val selectedInFilter = filtered.filter { it.track.included }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "按专辑批量设置",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "先筛选专辑，再全选或逐首勾选，最后写入新专辑名。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filterAlbum == null,
                        onClick = { filterAlbum = null },
                        label = { Text("全部专辑 ${entries.size}") },
                    )
                    albumOptions.forEach { album ->
                        val label = album.ifBlank { "未设置" }
                        FilterChip(
                            selected = filterAlbum == album,
                            onClick = {
                                filterAlbum = album
                                if (album.isNotBlank()) newAlbum = album
                            },
                            label = { Text("$label ${albumCounts[album] ?: 0}") },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onSetAllIncluded(true) }) {
                        Text("全选所有")
                    }
                    TextButton(onClick = { onSetAllIncluded(false) }) {
                        Text("全不选")
                    }
                    TextButton(onClick = { filtered.forEach { it.track.included = true } }) {
                        Text("全选当前")
                    }
                    TextButton(onClick = { filtered.forEach { it.track.included = false } }) {
                        Text("取消当前")
                    }
                }

                Text(
                    "当前筛选 ${filtered.size} 首 · 已勾选 ${selectedInFilter.size} 首",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                LazyColumn(Modifier.weight(1f)) {
                    if (filtered.isEmpty()) {
                        item(key = "empty-album-filter") {
                            Text(
                                "这个筛选下没有歌曲。",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        items(filtered, key = { it.track.file.absolutePath }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { item.track.included = !item.track.included }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .alpha(if (item.track.included) 1f else 0.45f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = item.track.included,
                                    onCheckedChange = { item.track.included = it },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${item.group.displayName} · ${item.album}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = newAlbum,
                        onValueChange = { newAlbum = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("要写入的专辑名") },
                        placeholder = { Text(DEFAULT_ALBUM) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val targetAlbum = newAlbum.trim().ifBlank { DEFAULT_ALBUM }
                            onSetTracksAlbum(selectedInFilter.map { it.track }, targetAlbum)
                            newAlbum = targetAlbum
                            filterAlbum = targetAlbum
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedInFilter.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("写入当前已勾选的 ${selectedInFilter.size} 首")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GroupCard(
    group: ArtistGroup,
    onPickCover: () -> Unit,
    onPickBilibiliCover: () -> Unit,
    onRename: () -> Unit,
    onRenameFiles: () -> Unit,
    onEditAlbum: () -> Unit,
    onShowSongs: () -> Unit,
    onManageFolders: () -> Unit,
    writeStates: Map<String, TrackWriteResult>,
) {
    val successCount = group.tracks.count {
        writeStates[it.file.absolutePath]?.state == TrackWriteState.SUCCESS
    }
    val failedCount = group.tracks.count {
        writeStates[it.file.absolutePath]?.state == TrackWriteState.FAILED
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            CoverThumb(cover = group.cover, size = 88.dp, onClick = onPickCover)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "修改歌手名",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SmallTag("${group.tracks.size} 首")
                    if (group.coverSource == CoverSource.AUTO) {
                        SmallTag(
                            "自动封面",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (group.coverSource == CoverSource.BILIBILI) {
                        SmallTag(
                            "B站封面",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    if (group.displayName == "未知歌手") {
                        SmallTag(
                            "未识别歌手",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (successCount > 0) {
                        SmallTag(
                            "已写入 $successCount",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (failedCount > 0) {
                        SmallTag(
                            "失败 $failedCount",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                if (group.displayName == "未知歌手") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点“查看歌曲”可逐首填写歌手",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (group.album.isBlank()) "未设置专辑" else "专辑：${group.album}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (group.album.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = onEditAlbum,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(if (group.album.isBlank()) "设置专辑" else "改专辑")
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    group.tracks.take(3).joinToString(" · ") { it.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TextButton(onClick = onShowSongs, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("查看歌曲")
                    }
                    TextButton(onClick = onPickCover, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(if (group.cover == null) "选封面" else "换封面")
                    }
                    TextButton(
                        onClick = onPickBilibiliCover,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("B站封面")
                    }
                    if (group.displayName != "未知歌手") {
                        TextButton(
                            onClick = onRenameFiles,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text("按歌手改名")
                        }
                    }
                    if (group.displayName == "未知歌手") {
                        TextButton(onClick = onManageFolders, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("按文件夹归类")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverThumb(cover: ByteArray?, size: Dp, onClick: (() -> Unit)? = null) {
    val bitmap = remember(cover) { cover?.let { decodeThumbnail(it) } }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                )
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(size * 0.42f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        if (onClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "选择封面",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallTag(
    text: String,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BottomWriteBar(ui: UiState, onWrite: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${ui.totalIncluded} 首待写入",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val missingCovers = ui.groups.count { it.includedCount > 0 && it.cover == null }
                Text(
                    if (missingCovers > 0) {
                        "$missingCovers 组未设置封面，仍会写入歌手"
                    } else {
                        "封面已就绪"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missingCovers > 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onWrite,
                enabled = ui.totalIncluded > 0 && !ui.writing,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(50.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始写入")
            }
        }
    }
}

@Composable
private fun EmptyState(onChangeFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("没有找到支持的音频文件", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "换一个包含 mp3、m4a 等文件的文件夹试试。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onChangeFolder, shape = RoundedCornerShape(14.dp)) {
            Text("更换文件夹")
        }
    }
}

@Composable

private fun SongListDialog(
    group: ArtistGroup,
    onAssignArtist: (Track, String, Boolean) -> Unit,
    onClearManualArtist: (Track) -> Unit,
    onSetTrackCover: (Track, ByteArray) -> Unit,
    onClearTrackCover: (Track) -> Unit,
    onSetTracksAlbum: (List<Track>, String) -> Unit,
    writeStates: Map<String, TrackWriteResult>,
    onDismiss: () -> Unit,
) {
    var editingTrack by remember { mutableStateOf<Track?>(null) }
    var filter by remember { mutableStateOf(WriteFilter.ALL) }

    val writtenCount = group.tracks.count { isWritten(writeStates[it.file.absolutePath]) }
    val visibleTracks = when (filter) {
        WriteFilter.ALL -> group.tracks.toList()
        WriteFilter.WRITTEN -> group.tracks.filter { isWritten(writeStates[it.file.absolutePath]) }
        WriteFilter.UNWRITTEN -> group.tracks.filterNot { isWritten(writeStates[it.file.absolutePath]) }
    }
    val visibleSet = visibleTracks.toSet()

    LaunchedEffect(group.tracks.isEmpty()) {
        if (group.tracks.isEmpty()) onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${group.tracks.size} 首 · 已选 ${group.includedCount} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (group.album.isBlank()) "专辑：未设置" else "专辑：${group.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                if (group.displayName == "未知歌手") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            "没有识别到歌手。点每首右侧的编辑按钮填写歌手，保存后会自动归到对应歌手分组。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WriteFilter.values().forEach { option ->
                        val count = when (option) {
                            WriteFilter.ALL -> group.tracks.size
                            WriteFilter.WRITTEN -> writtenCount
                            WriteFilter.UNWRITTEN -> group.tracks.size - writtenCount
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text("${option.label} $count") },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        group.tracks.forEach { it.included = it in visibleSet }
                    }) {
                        Text("仅选当前")
                    }
                    TextButton(onClick = {
                        visibleTracks.forEach { it.included = false }
                    }) {
                        Text("取消当前")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "显示 ${visibleTracks.size} 首 · 已选 ${group.includedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                LazyColumn(Modifier.weight(1f)) {
                    if (visibleTracks.isEmpty()) {
                        item(key = "empty-filter") {
                            Text(
                                if (filter == WriteFilter.WRITTEN) {
                                    "还没有已写入的歌曲"
                                } else {
                                    "没有未写入的歌曲"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        items(visibleTracks, key = { it.file.absolutePath }) { track ->
                            TrackRow(
                                track = track,
                                showArtistHint = group.displayName == "未知歌手",
                                writeResult = writeStates[track.file.absolutePath],
                                onEdit = { editingTrack = track },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "取消勾选的歌曲不会写入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                        Text("完成")
                    }
                }
            }
        }
    }

    editingTrack?.let { track ->
        TrackEditDialog(
            track = track,
            group = group,
            onAssignArtist = onAssignArtist,
            onClearManualArtist = onClearManualArtist,
            onSetTrackCover = onSetTrackCover,
            onClearTrackCover = onClearTrackCover,
            onSetTracksAlbum = onSetTracksAlbum,
            onDismiss = { editingTrack = null },
        )
    }
}

@Composable
private fun FileRenameDialog(
    group: ArtistGroup,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val selectedTracks = group.tracks.filter { it.included }
    val preview = selectedTracks.take(4).map { track ->
        AudioFileName.buildFileName(group.displayName, track.title, track.file.extension)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按歌手重命名文件") },
        text = {
            Column {
                Text(
                    "只处理已勾选的 ${selectedTracks.size} 首歌曲，改成「歌手名 - 歌名」格式，扩展名保持不变。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    preview.forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selectedTracks.size > preview.size) {
                        Text(
                            "…另有 ${selectedTracks.size - preview.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "未勾选的歌曲不会改名。目标文件名已存在时会自动加 (2)，不会覆盖已有文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedTracks.isNotEmpty() && group.displayName != "未知歌手",
            ) {
                Text("开始重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun BilibiliImportDialog(
    root: File?,
    onImport: suspend (BilibiliImportRequest) -> Result<File>,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var album by remember { mutableStateOf(DEFAULT_ALBUM) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canImport = input.isNotBlank() && artist.isNotBlank() && !importing

    Dialog(
        onDismissRequest = { if (!importing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "新增 B 站音频",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "下载公开视频的音频轨，按你填写的歌手和歌名保存并写入标签。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !importing) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                ) {
                    Text(
                        "保存到：${root?.absolutePath ?: "未选择音乐文件夹"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("B 站链接 / BV 号 / av 号") },
                        placeholder = { Text("例如：https://www.bilibili.com/video/BV...") },
                        enabled = !importing,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("歌手名（必填）") },
                        singleLine = true,
                        enabled = !importing,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("歌曲名") },
                        placeholder = { Text("留空则使用视频标题") },
                        singleLine = true,
                        enabled = !importing,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("专辑名") },
                        placeholder = { Text(DEFAULT_ALBUM) },
                        singleLine = true,
                        enabled = !importing,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "会优先保存到同名歌手的现有文件夹，否则保存到当前音乐文件夹。文件名使用「歌手名 - 歌曲名.m4a」，同名自动加 (2)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    status?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (failed) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "正在下载并写入…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !importing) {
                        Text("关闭")
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                importing = true
                                failed = false
                                status = "正在解析视频、下载音频并写入标签…"
                                try {
                                    val result = onImport(
                                        BilibiliImportRequest(
                                            input = input.trim(),
                                            artist = artist.trim(),
                                            title = title.trim(),
                                            album = album.trim().ifBlank { DEFAULT_ALBUM },
                                        )
                                    )
                                    result.onSuccess { file ->
                                        status = "已保存：${file.name}"
                                        failed = false
                                        input = ""
                                        title = ""
                                    }.onFailure { error ->
                                        status = "失败：${error.message ?: "未知错误"}"
                                        failed = true
                                    }
                                } catch (error: Throwable) {
                                    status = "失败：${error.message ?: error.javaClass.simpleName}"
                                    failed = true
                                } finally {
                                    importing = false
                                }
                            }
                        },
                        enabled = canImport,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("下载并写入")
                    }
                }
            }
        }
    }
}

@Composable
private fun BilibiliCoverDialog(
    onDismiss: () -> Unit,
    onUse: (BilibiliCover) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fetched by remember { mutableStateOf<BilibiliCover?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchCover() {
        val text = input.trim()
        if (text.isBlank()) {
            errorMessage = "请先粘贴视频链接或 BV 号"
            return
        }
        if (loading) return
        loading = true
        errorMessage = null
        fetched = null
        scope.launch {
            BilibiliCoverClient.fetch(text)
                .onSuccess { fetched = it }
                .onFailure { errorMessage = it.message ?: "获取失败，请检查网络" }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从 B 站获取封面") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "粘贴视频链接、分享文字或 BV 号，也支持 b23.tv 短链接。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorMessage = null
                        fetched = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("视频链接或 BV 号") },
                    placeholder = { Text("https://www.bilibili.com/video/BV...") },
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { fetchCover() },
                        enabled = !loading,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (fetched == null) "获取封面" else "重新获取")
                    }
                    if (loading) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                errorMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                fetched?.let { cover ->
                    val preview = remember(cover.jpeg) { decodeThumbnail(cover.jpeg, 480) }
                    Spacer(Modifier.height(12.dp))
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "B站视频封面",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        cover.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (cover.bvid.isNotBlank()) {
                        Text(
                            "BV号：${cover.bvid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "使用时会居中裁成正方形 JPEG。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { fetched?.let(onUse) },
                enabled = fetched != null,
            ) {
                Text("使用封面")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun TrackRow(
    track: Track,
    showArtistHint: Boolean,
    writeResult: TrackWriteResult?,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { track.included = !track.included }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alpha(if (track.included) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = track.included,
            onCheckedChange = { track.included = it },
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArtistHint) {
                Text(
                    "未设置歌手 · 点右侧编辑填写",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (track.cover != null) {
                Spacer(Modifier.height(3.dp))
                SmallTag(
                    "单曲封面",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            writeResult?.let { result ->
                Spacer(Modifier.height(3.dp))
                when (result.state) {
                    TrackWriteState.SUCCESS -> SmallTag("已写入")
                    TrackWriteState.SKIPPED -> SmallTag(
                        "已跳过",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TrackWriteState.FAILED -> SmallTag(
                        "写入失败",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑歌手和歌名",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackEditDialog(
    track: Track,
    group: ArtistGroup,
    onAssignArtist: (Track, String, Boolean) -> Unit,
    onClearManualArtist: (Track) -> Unit,
    onSetTrackCover: (Track, ByteArray) -> Unit,
    onClearTrackCover: (Track) -> Unit,
    onSetTracksAlbum: (List<Track>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var titleValue by remember(track.file.absolutePath) { mutableStateOf(track.title) }
    var artistValue by remember(track.file.absolutePath) {
        mutableStateOf(if (group.displayName == "未知歌手") "" else group.displayName)
    }
    val effectiveAlbum = track.albumFor(group.album, group.albumExplicit)
    var albumValue by remember(track.file.absolutePath, effectiveAlbum) {
        mutableStateOf(effectiveAlbum)
    }
    var rememberFolder by remember(track.file.absolutePath) { mutableStateOf(false) }
    var pendingCover by remember(track.file.absolutePath) { mutableStateOf<ByteArray?>(null) }
    var removeCover by remember(track.file.absolutePath) { mutableStateOf(false) }
    var showBilibili by remember(track.file.absolutePath) { mutableStateOf(false) }
    val displayedCover = pendingCover ?: if (removeCover) null else track.cover
    val displayedBitmap = remember(displayedCover) {
        displayedCover?.let { decodeThumbnail(it, 320) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑歌曲信息") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = artistValue,
                    onValueChange = { artistValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌手") },
                    placeholder = { Text("未识别到时在这里填写") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌名") },
                    singleLine = false,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = albumValue,
                    onValueChange = { albumValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("专辑") },
                    placeholder = {
                        Text("留空则使用本组或原专辑：${group.album.ifBlank { DEFAULT_ALBUM }}")
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (displayedBitmap != null) {
                                Image(
                                    bitmap = displayedBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "单曲封面",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    if (displayedCover != null) {
                                        "保存后写入时优先使用这张，覆盖歌手组封面"
                                    } else {
                                        "未设置时使用歌手组封面"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row {
                            TextButton(
                                onClick = { showBilibili = true },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Text(if (displayedCover == null) "从 B 站获取" else "换 B 站封面")
                            }
                            if (displayedCover != null) {
                                TextButton(
                                    onClick = {
                                        pendingCover = null
                                        removeCover = track.cover != null
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) {
                                    Text("清除")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberFolder = !rememberFolder }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberFolder,
                        onCheckedChange = { rememberFolder = it },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "记住这个文件夹",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "以后此文件夹里识别不到歌手的歌曲自动归为这个歌手。" +
                                "混放多位歌手的文件夹请不要勾选。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "文件名：${track.file.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (group.displayName == "未知歌手") {
                    Text(
                        "填写歌手并保存后，这首歌会自动移到对应歌手分组。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (track.manualArtist) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前歌手是手动指定的，重新扫描也会保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = {
                            onClearManualArtist(track)
                            onDismiss()
                        }
                    ) {
                        Text("清除手动歌手")
                    }
                }
                TextButton(onClick = { titleValue = track.parsedTitle }) {
                    Text("恢复解析歌名")
                }
                if (group.album.isNotBlank() && albumValue != group.album) {
                    TextButton(onClick = { albumValue = group.album }) {
                        Text("使用本组专辑：${group.album}")
                    }
                }
                if (track.albumExplicit) {
                    TextButton(onClick = { albumValue = "" }) {
                        Text("清除单曲专辑覆盖")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    track.title = titleValue.trim().ifBlank { track.parsedTitle }
                    val cleanAlbum = albumValue.trim()
                    if (cleanAlbum != effectiveAlbum) {
                        onSetTracksAlbum(listOf(track), cleanAlbum)
                    }
                    val artist = artistValue.trim()
                    if (
                        artist.isNotBlank() &&
                        (FilenameParser.normalize(artist) != group.key || rememberFolder)
                    ) {
                        onAssignArtist(track, artist, rememberFolder)
                    }
                    pendingCover?.let { onSetTrackCover(track, it) }
                    if (removeCover) onClearTrackCover(track)
                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (showBilibili) {
        BilibiliCoverDialog(
            onDismiss = { showBilibili = false },
            onUse = { cover ->
                pendingCover = cover.jpeg
                removeCover = false
                showBilibili = false
            },
        )
    }
}

@Composable
private fun AlbumNameDialog(
    group: ArtistGroup,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(group.key) { mutableStateOf(group.album) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置专辑") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("专辑名") },
                    placeholder = { Text(DEFAULT_ALBUM) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "会写入「${group.displayName}」组里没有单独设置专辑的 ${group.tracks.size} 首歌。单曲可以在编辑里单独覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private enum class HistoryFilter { ALL, SUCCESS, FAILED, SKIPPED }

private data class HistorySection(
    val state: TrackWriteState,
    val records: List<WriteRecord>,
)

@Composable
private fun WriteHistoryDialog(
    records: List<WriteRecord>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    val counts = remember(records) {
        records.groupingBy { it.state }.eachCount()
    }
    val sections = remember(records, filter) {
        val newestFirst = records.asReversed()
        val states = when (filter) {
            HistoryFilter.ALL -> listOf(
                TrackWriteState.FAILED,
                TrackWriteState.SKIPPED,
                TrackWriteState.SUCCESS,
            )
            HistoryFilter.SUCCESS -> listOf(TrackWriteState.SUCCESS)
            HistoryFilter.FAILED -> listOf(TrackWriteState.FAILED)
            HistoryFilter.SKIPPED -> listOf(TrackWriteState.SKIPPED)
        }
        states.mapNotNull { state ->
            newestFirst
                .filter { it.state == state }
                .takeIf { it.isNotEmpty() }
                ?.let { HistorySection(state, it) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "写入记录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "成功 ${counts[TrackWriteState.SUCCESS] ?: 0} · " +
                                "跳过 ${counts[TrackWriteState.SKIPPED] ?: 0} · " +
                                "失败 ${counts[TrackWriteState.FAILED] ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == HistoryFilter.ALL,
                        onClick = { filter = HistoryFilter.ALL },
                        label = { Text("全部 ${records.size}") },
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.SUCCESS,
                        onClick = { filter = HistoryFilter.SUCCESS },
                        label = { Text("成功 ${counts[TrackWriteState.SUCCESS] ?: 0}") },
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.FAILED,
                        onClick = { filter = HistoryFilter.FAILED },
                        label = { Text("失败 ${counts[TrackWriteState.FAILED] ?: 0}") },
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.SKIPPED,
                        onClick = { filter = HistoryFilter.SKIPPED },
                        label = { Text("跳过 ${counts[TrackWriteState.SKIPPED] ?: 0}") },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                if (sections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (records.isEmpty()) "还没有写入记录。" else "这个分类下没有记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        sections.forEach { section ->
                            item(key = "history_header_${section.state.name}") {
                                HistorySectionHeader(
                                    state = section.state,
                                    count = section.records.size,
                                )
                            }
                            items(section.records) { record ->
                                HistoryRow(record)
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "只记录本机处理结果，不修改歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (records.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text("清空记录")
                        }
                    }
                    Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                        Text("完成")
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空写入记录？") },
            text = { Text("只会清空这张列表，不会改动已经写入的歌曲文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun HistorySectionHeader(state: TrackWriteState, count: Int) {
    val (title, container, content) = when (state) {
        TrackWriteState.FAILED -> Triple(
            "写入失败",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        TrackWriteState.SKIPPED -> Triple(
            "已跳过",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        TrackWriteState.SUCCESS -> Triple(
            "写入成功",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
        Text(
            "$count 首",
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun HistoryRow(record: WriteRecord) {
    val time = remember(record.finishedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.finishedAt))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (record.state) {
                TrackWriteState.SUCCESS -> SmallTag("成功")
                TrackWriteState.SKIPPED -> SmallTag(
                    "跳过",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TrackWriteState.FAILED -> SmallTag(
                    "失败",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                record.fileName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            buildString {
                append("歌手：")
                append(record.artist.ifBlank { "未设置" })
                if (record.album.isNotBlank()) {
                    append(" · 专辑：")
                    append(record.album)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!record.message.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                record.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (record.state == TrackWriteState.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderMemoryDialog(
    ui: UiState,
    onSave: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<FolderEntry?>(null) }

    val entries = remember(ui.groups, ui.folderMappings) {
        val byFolder = linkedMapOf<String, MutableList<Track>>()
        for (group in ui.groups) {
            for (track in group.tracks) {
                val parent = track.file.parentFile ?: continue
                byFolder.getOrPut(parent.absolutePath) { mutableListOf() }.add(track)
            }
        }
        (byFolder.keys + ui.folderMappings.keys)
            .distinct()
            .mapNotNull { path ->
                val tracks = byFolder[path].orEmpty()
                val mapped = ui.folderMappings[path]
                if (tracks.isEmpty() && mapped == null) return@mapNotNull null
                FolderEntry(
                    path = path,
                    name = path.trimEnd('/').substringAfterLast('/'),
                    totalCount = tracks.size,
                    unknownCount = tracks.count {
                        !it.manualArtist && FilenameParser.isUnknownArtistName(it.parsedArtist)
                    },
                    mappedArtist = mapped,
                )
            }
            .filter { it.unknownCount > 0 || it.mappedArtist != null }
            .sortedWith(
                compareByDescending<FolderEntry> { it.unknownCount }
                    .thenBy { it.path.lowercase(Locale.ROOT) }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "文件夹歌手记忆",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "只补识别不到歌手的歌曲，不覆盖文件名和标签里的歌手",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "适合“一个文件夹一位歌手”。设置后，这个文件夹里以后新增、但识别不到歌手的歌曲也会自动归到这个歌手。混放多位歌手的文件夹请不要设置。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "没有需要设置的文件夹。\n所有歌曲都已经识别到歌手了。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(entries, key = { it.path }) { entry ->
                            FolderMemoryRow(
                                entry = entry,
                                onEdit = { editing = entry },
                                onClear = { onClear(entry.path) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "识别不到歌手时才会使用文件夹记忆",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                        Text("完成")
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        FolderArtistNameDialog(
            entry = entry,
            onDismiss = { editing = null },
            onConfirm = { artist ->
                onSave(entry.path, artist)
                editing = null
            },
        )
    }
}

private data class FolderEntry(
    val path: String,
    val name: String,
    val totalCount: Int,
    val unknownCount: Int,
    val mappedArtist: String?,
)

@Composable
private fun FolderMemoryRow(
    entry: FolderEntry,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.name.ifBlank { entry.path },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.mappedArtist != null) {
                SmallTag("→ ${entry.mappedArtist}")
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            entry.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (entry.unknownCount > 0) {
                    "${entry.unknownCount} 首未识别 · 共 ${entry.totalCount} 首"
                } else {
                    "共 ${entry.totalCount} 首"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.unknownCount > 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (entry.mappedArtist != null) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("清除")
                }
            }
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (entry.mappedArtist == null) "设置" else "修改")
            }
        }
    }
}

@Composable
private fun FolderArtistNameDialog(
    entry: FolderEntry,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(entry.path) { mutableStateOf(entry.mappedArtist.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置文件夹歌手") },
        text = {
            Column {
                Text(
                    entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌手名") },
                    placeholder = { Text("例如：林俊杰") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "只影响这个文件夹里识别不到歌手的歌曲；以后新增的歌曲也会自动归到这个歌手。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (FilenameParser.isGenericFolder(entry.name)) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "提醒：这个文件夹像是混放多位歌手的公共目录，设置后可能归错，建议先确认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ArtistNameDialog(
    group: ArtistGroup,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(group.key) {
        mutableStateOf(if (group.displayName == "未知歌手") "" else group.displayName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改歌手名") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌手名") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "会写入本组 ${group.tracks.size} 首歌曲的歌手标签。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun decodeThumbnail(bytes: ByteArray, maxSize: Int = 320): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}



