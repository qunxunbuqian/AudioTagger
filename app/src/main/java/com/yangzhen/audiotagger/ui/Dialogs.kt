package com.yangzhen.audiotagger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yangzhen.audiotagger.WriteProgress
import com.yangzhen.audiotagger.WriteSummary
import kotlin.math.roundToInt

@Composable
fun ScanningDialog() {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        "正在扫描文件夹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "歌曲多时可能需要几秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun WritingDialog(progress: WriteProgress, stopping: Boolean, onCancel: () -> Unit) {
    val fraction = if (progress.total <= 0) {
        0f
    } else {
        progress.current.toFloat() / progress.total.toFloat()
    }
    val percent = (fraction * 100f).roundToInt()

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "正在写入",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${progress.current} / ${progress.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    progress.currentName.ifBlank { "准备中…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountBox("成功", progress.success, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    CountBox("跳过", progress.skipped, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    CountBox("失败", progress.failed, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                if (stopping) {
                    Text(
                        "正在停止，当前文件完成后结束…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryDialog(
    summary: WriteSummary,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onShowHistory: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val success = summary.failed == 0 && !summary.cancelled

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when {
                                summary.cancelled -> "已停止"
                                summary.failed == 0 -> "写入完成"
                                else -> "部分歌曲写入失败"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "共 ${summary.total} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountBox("成功", summary.success, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    CountBox("跳过", summary.skipped, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    CountBox("失败", summary.failed, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }

                if (summary.renamed > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "其中 ${summary.renamed} 个伪装成 .mp3 的 M4A 已修复并改名为 .m4a。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (summary.cancelled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "已写入的歌曲不会回滚。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                if (summary.errors.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "失败详情",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(summary.errors.joinToString("\n")))
                        }) {
                            Text("复制")
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        LazyColumn(Modifier.padding(10.dp)) {
                            items(summary.errors) { error ->
                                Text(
                                    error,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("返回列表")
                    }
                    TextButton(onClick = onShowHistory) {
                        Text("查看记录")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRescan, shape = RoundedCornerShape(14.dp)) {
                        Text("重新扫描")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBox(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

