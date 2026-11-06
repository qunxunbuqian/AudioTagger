package com.yangzhen.audiotagger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhen.audiotagger.TaggerViewModel
import com.yangzhen.audiotagger.util.FilenameParser
import com.yangzhen.audiotagger.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun TaggerApp(viewModel: TaggerViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pendingCoverGroup by remember { mutableStateOf<String?>(null) }

    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val groupKey = pendingCoverGroup
        pendingCoverGroup = null
        if (uri != null && groupKey != null) {
            viewModel.setCover(groupKey, uri)
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val activity = context.findActivity()
    DisposableEffect(ui.writing) {
        if (ui.writing) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(enabled = ui.writing) { }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !ui.permissionGranted -> PermissionScreen(
                    onOpenSettings = { openAllFilesSettings(context) },
                    onRefresh = { viewModel.refreshPermission() },
                )

                ui.root == null || showPicker -> FolderPickerScreen(
                    initialPath = ui.lastRootPath,
                    canClose = ui.root != null,
                    onClose = { showPicker = false },
                    onSelect = { folder ->
                        showPicker = false
                        viewModel.setRoot(folder)
                    },
                )

                else -> GroupsScreen(
                    ui = ui,
                    onChangeFolder = { showPicker = true },
                    onRescan = { viewModel.rescan() },
                    onPickCover = { key ->
                        pendingCoverGroup = key
                        coverLauncher.launch("image/*")
                    },
                    onSetGroupBilibiliCover = { key, bytes ->
                        viewModel.setGroupBilibiliCover(key, bytes)
                    },
                    onRenameGroup = { key, name -> viewModel.renameGroup(key, name) },
                    onRenameGroupFiles = { key -> viewModel.renameGroupFiles(key) },
                    onAssignArtist = { track, artist, rememberFolder ->
                        viewModel.assignTrackArtist(track, artist, rememberFolder)
                    },
                    onClearManualArtist = { track -> viewModel.clearManualArtist(track) },
                    onSetTrackCover = { track, bytes -> viewModel.setTrackCover(track, bytes) },
                    onClearTrackCover = { track -> viewModel.clearTrackCover(track) },
                    onSetFolderArtist = { path, artist -> viewModel.setFolderArtist(path, artist) },
                    onClearFolderArtist = { path -> viewModel.clearFolderArtist(path) },
                    onSetGroupAlbum = { key, album -> viewModel.setGroupAlbum(key, album) },
                    onSetAllIncluded = { included -> viewModel.setAllIncluded(included) },
                    onSetTracksAlbum = { tracks, album -> viewModel.setTracksAlbum(tracks, album) },
                    onWrite = { viewModel.writeAll() },
                    onToggleWriteTitle = { viewModel.setWriteTitle(it) },
                    onToggleWriteAlbum = { viewModel.setWriteAlbum(it) },
                    onToggleSkip = { viewModel.setSkipIfDone(it) },
                    onOpenHistory = { viewModel.openHistory() },
                    onCloseHistory = { viewModel.closeHistory() },
                    onClearHistory = { viewModel.clearHistory() },
                    onImportBilibiliAudio = { request -> viewModel.importBilibiliAudio(request) },
                )
            }
        }
    }

    if (ui.scanning) {
        ScanningDialog()
    }

    ui.progress?.let { progress ->
        WritingDialog(
            progress = progress,
            stopping = ui.stopping,
            onCancel = { viewModel.cancelWrite() },
        )
    }

    ui.summary?.let { summary ->
        SummaryDialog(
            summary = summary,
            onDismiss = { viewModel.dismissSummary() },
            onRescan = {
                viewModel.dismissSummary()
                viewModel.rescan()
            },
            onShowHistory = {
                viewModel.dismissSummary()
                viewModel.openHistory()
            },
        )
    }
}

@Composable
private fun PermissionScreen(onOpenSettings: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "需要“所有文件访问”权限",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "安卓的普通权限只能读取媒体库。要直接修改歌曲内嵌的歌手、歌名和封面，需要允许访问手机存储中的文件。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "应用只处理你手动选择的文件夹，不会上传任何文件。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("去授权")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("已授权，重新检查")
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "授权路径：设置 → 应用 → 音频标签工具 → 特殊应用权限 → 所有文件访问",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerScreen(
    initialPath: String?,
    canClose: Boolean,
    onClose: () -> Unit,
    onSelect: (File) -> Unit,
) {
    val context = LocalContext.current
    val primaryRoot = remember { StorageUtils.primaryRoot() }
    val roots = remember { StorageUtils.roots(context) }

    val startFolder = remember(initialPath) {
        val candidate = initialPath?.let { File(it) }
        if (candidate?.isDirectory == true) candidate else primaryRoot
    }
    var currentPath by rememberSaveable { mutableStateOf(startFolder.absolutePath) }
    val current = remember(currentPath) { File(currentPath) }
    var audioCount by remember(currentPath) { mutableStateOf<Int?>(null) }

    val canGoUp = remember(currentPath) {
        val parent = current.parentFile
        parent != null && roots.any { root ->
            parent.absolutePath == root.absolutePath ||
                parent.absolutePath.startsWith(root.absolutePath + File.separator)
        }
    }

    BackHandler(enabled = canGoUp) {
        current.parentFile?.let { currentPath = it.absolutePath }
    }

    LaunchedEffect(currentPath) {
        audioCount = null
        audioCount = withContext(Dispatchers.IO) {
            runCatching {
                current.walkTopDown()
                    .onEnter { dir -> !dir.name.startsWith(".") && dir.name != "Android" }
                    .onFail { _, _ -> }
                    .filter { it.isFile && FilenameParser.isSupportedAudio(it.name) }
                    .take(5000)
                    .count()
            }.getOrDefault(0)
        }
    }

    val directories = remember(currentPath) {
        current.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("选择音乐文件夹") },
            navigationIcon = {
                if (canGoUp || canClose) {
                    IconButton(onClick = {
                        if (canGoUp) {
                            current.parentFile?.let { currentPath = it.absolutePath }
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    current.absolutePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (val count = audioCount) {
                        null -> "正在统计音频数量…"
                        0 -> "没有找到支持的音频文件"
                        5000 -> "含子文件夹：5000+ 个音频"
                        else -> "含子文件夹：$count 个音频"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onSelect(current) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("选择此文件夹")
                }
            }
        }

        if (roots.size > 1) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                roots.forEachIndexed { index, root ->
                    val selected = currentPath == root.absolutePath ||
                        currentPath.startsWith(root.absolutePath + File.separator)
                    FilterChip(
                        selected = selected,
                        onClick = { currentPath = root.absolutePath },
                        label = {
                            Text(if (index == 0) "内部存储" else "存储卡 $index")
                        },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 4.dp,
            ),
        ) {
            if (canGoUp) {
                item(key = "up") {
                    DirectoryRow(
                        name = "上一级",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { current.parentFile?.let { currentPath = it.absolutePath } },
                    )
                }
            }

            items(directories, key = { it.absolutePath }) { directory ->
                DirectoryRow(
                    name = directory.name,
                    icon = Icons.Default.Folder,
                    onClick = { currentPath = directory.absolutePath },
                )
            }

            if (directories.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "这个文件夹没有子文件夹，可以直接选择它。",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item(key = "formats") {
                Text(
                    "支持 mp3、m4a、flac、ogg、opus、wav、aiff",
                    modifier = Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DirectoryRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openAllFilesSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    runCatching { context.startActivity(appIntent) }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

private fun Context.findActivity(): Activity? {
    var target: Context? = this
    while (target is ContextWrapper) {
        if (target is Activity) return target
        target = target.baseContext
    }
    return target as? Activity
}


