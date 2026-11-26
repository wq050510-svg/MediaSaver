package com.personal.mediasaver.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.personal.mediasaver.MediaSaverApp
import com.personal.mediasaver.domain.model.MediaItem
import com.personal.mediasaver.domain.model.MediaItemType
import com.personal.mediasaver.domain.model.MediaType
import com.personal.mediasaver.domain.model.ParsedMedia
import com.personal.mediasaver.domain.model.Platform
import com.personal.mediasaver.domain.sanitizer.SanitizedLink
import com.personal.mediasaver.ui.theme.platformAccent
import com.personal.mediasaver.worker.DownloadWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParsePreviewBottomSheet(
    sanitizedLink: SanitizedLink,
    isDarkTheme: Boolean = false,
    onDismiss: () -> Unit,
    onEnqueued: (taskId: String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaSaverApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var retryKey by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var parsedMedia by remember { mutableStateOf<ParsedMedia?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedQualityIndex by remember { mutableIntStateOf(0) }
    val selectedGalleryIndices = remember { mutableStateListOf<Int>() }
    var isEnqueuing by remember { mutableStateOf(false) }

    LaunchedEffect(sanitizedLink.cleanUrl, retryKey) {
        isLoading = true
        errorMessage = ""
        parsedMedia = null
        selectedQualityIndex = 0
        selectedGalleryIndices.clear()

        val result = app.container.engineBridge.parseUrl(
            url = sanitizedLink.cleanUrl,
            platform = sanitizedLink.platform,
        )
        if (result.isSuccess) {
            parsedMedia = result
            if (result.mediaTypeEnum != MediaType.VIDEO) {
                selectedGalleryIndices.addAll(result.items.indices)
            }
        } else {
            errorMessage = result.errorMessage.ifBlank { "解析失败，请检查链接有效性或网络配置。" }
        }
        isLoading = false
    }

    val accent = platformAccent(sanitizedLink.platform, isDarkTheme)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Header row: platform badge + clean URL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Surface(
                    color = accent.container,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = sanitizedLink.platform.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent.onContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = sanitizedLink.cleanUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text(
                            text = "正在端侧解析媒体资源…",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "已自动应用端侧解析与分流代理规则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                errorMessage.isNotBlank() -> {
                    ParseErrorSection(
                        errorMessage = errorMessage,
                        onCopyError = {
                            copyTextToClipboard(context, "MediaSaver 解析错误日志", errorMessage)
                            Toast.makeText(context, "已复制完整错误日志", Toast.LENGTH_SHORT).show()
                        },
                        onRetry = { retryKey++ },
                        onDismiss = onDismiss,
                    )
                }

                parsedMedia != null -> {
                    val media = parsedMedia!!
                    val isGalleryOrMixed = media.mediaTypeEnum != MediaType.VIDEO && media.items.isNotEmpty()
                    val hasFfmpeg = remember { app.container.engineBridge.getFfmpegBinaryPath() != null }

                    // Scrollable preview & selection area (capped height so bottom actions stay pinned!)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Cover + title + author summary card
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (media.bestCover.isNotBlank()) {
                                    AsyncImage(
                                        model = media.bestCover,
                                        contentDescription = media.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = media.title.ifBlank { "未命名作品" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (media.author.isNotBlank()) {
                                        Text(
                                            text = "@${media.author}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = when (media.mediaTypeEnum) {
                                                MediaType.VIDEO -> "视频作品"
                                                MediaType.GALLERY -> "高清图集 · 共 ${media.items.size} 张"
                                                MediaType.MIXED -> "图文混排 · 共 ${media.items.size} 项"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (!isGalleryOrMixed) {
                            // Video quality selector
                            Text(
                                text = "选择清晰度 / 音频",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (media.platformEnum == Platform.BILIBILI && !hasFfmpeg) {
                                Text(
                                    text = "提示：未检测到 FFmpeg，B 站已自动降级为单流格式。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val qualityItems = media.items.ifEmpty {
                                    listOf(MediaItem(quality = "默认最佳画质", formatId = "best"))
                                }
                                qualityItems.forEachIndexed { idx, item ->
                                    FilterChip(
                                        selected = selectedQualityIndex == idx,
                                        onClick = { selectedQualityIndex = idx },
                                        shape = RoundedCornerShape(10.dp),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (item.itemType == MediaItemType.AUDIO) {
                                                    Icons.Rounded.MusicNote
                                                } else {
                                                    Icons.Rounded.HighQuality
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        label = { Text(item.quality.ifBlank { "选项 ${idx + 1}" }) },
                                    )
                                }
                            }
                        } else {
                            // 3-column adaptive gallery grid selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "点击勾选要保存的内容（${selectedGalleryIndices.size}/${media.items.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val allSelected = selectedGalleryIndices.size == media.items.size
                                TextButton(
                                    onClick = {
                                        if (allSelected) {
                                            selectedGalleryIndices.clear()
                                        } else {
                                            selectedGalleryIndices.clear()
                                            selectedGalleryIndices.addAll(media.items.indices)
                                        }
                                    }
                                ) {
                                    Text(if (allSelected) "取消全选" else "全选")
                                }
                            }

                            val indexedItems = media.items.mapIndexed { index, item -> index to item }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                indexedItems.chunked(3).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowItems.forEach { (idx, item) ->
                                            val isSelected = selectedGalleryIndices.contains(idx)
                                            GalleryGridTile(
                                                item = item,
                                                index = idx,
                                                selected = isSelected,
                                                onToggle = {
                                                    if (isSelected) {
                                                        selectedGalleryIndices.remove(idx)
                                                    } else {
                                                        selectedGalleryIndices.add(idx)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pinned Bottom Action Bar (always visible!)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Text("取消")
                        }
                        Button(
                            enabled = !isEnqueuing && (!isGalleryOrMixed || selectedGalleryIndices.isNotEmpty()),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                isEnqueuing = true
                                scope.launch {
                                    val selectedFormat = if (!isGalleryOrMixed) {
                                        media.items.getOrNull(selectedQualityIndex)?.formatId
                                    } else {
                                        null
                                    }
                                    val indices = if (isGalleryOrMixed) {
                                        selectedGalleryIndices.sorted()
                                    } else {
                                        null
                                    }
                                    val targetUrl = media.resolvedUrl.ifBlank { sanitizedLink.cleanUrl }
                                    val taskId = DownloadWorker.enqueue(
                                        context = context,
                                        url = targetUrl,
                                        platform = media.platformEnum.takeIf { it != Platform.UNKNOWN }
                                            ?: sanitizedLink.platform,
                                        title = media.title,
                                        author = media.author,
                                        mediaType = media.mediaTypeEnum,
                                        thumbnailUrl = media.bestCover,
                                        formatId = selectedFormat,
                                        selectedIndices = indices,
                                    )
                                    Toast.makeText(context, "已加入后台下载队列", Toast.LENGTH_SHORT).show()
                                    onEnqueued(taskId)
                                }
                            },
                            modifier = Modifier
                                .weight(1.7f)
                                .height(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isGalleryOrMixed) "保存选中 (${selectedGalleryIndices.size})" else "保存到相册")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryGridTile(
    item: MediaItem,
    index: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onToggle),
    ) {
        val preview = item.thumbnail.ifBlank {
            if (item.itemType == MediaItemType.IMAGE) item.url else ""
        }
        if (preview.isNotBlank()) {
            AsyncImage(
                model = preview,
                contentDescription = item.quality,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = when (item.itemType) {
                    MediaItemType.VIDEO -> Icons.Rounded.Videocam
                    MediaItemType.AUDIO -> Icons.Rounded.AudioFile
                    MediaItemType.IMAGE -> Icons.Rounded.Image
                },
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Subtle selection dim/tint overlay
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            )
        }

        // Top-right selection badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Bottom-left type/index badge
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp),
        ) {
            Text(
                text = when (item.itemType) {
                    MediaItemType.VIDEO -> "视频 ${index + 1}"
                    MediaItemType.AUDIO -> "音频"
                    MediaItemType.IMAGE -> "${index + 1}"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ParseErrorSection(
    errorMessage: String,
    onCopyError: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "解析失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = errorMessage.take(600),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onCopyError,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制完整日志")
            }
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("重试解析")
            }
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("关闭")
        }
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
