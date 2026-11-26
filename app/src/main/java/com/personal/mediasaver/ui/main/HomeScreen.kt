package com.personal.mediasaver.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.personal.mediasaver.MediaSaverApp
import com.personal.mediasaver.R
import com.personal.mediasaver.domain.model.DownloadStatus
import com.personal.mediasaver.domain.model.DownloadTask
import com.personal.mediasaver.domain.model.MediaType
import com.personal.mediasaver.domain.model.Platform
import com.personal.mediasaver.domain.model.ThemeMode
import com.personal.mediasaver.domain.sanitizer.SanitizedLink
import com.personal.mediasaver.domain.sanitizer.UrlSanitizer
import com.personal.mediasaver.ui.share.ParsePreviewBottomSheet
import com.personal.mediasaver.ui.share.copyTextToClipboard
import com.personal.mediasaver.ui.theme.platformAccent
import com.personal.mediasaver.worker.DownloadWorker
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkTheme: Boolean,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaSaverApp
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var inputText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<Platform?>(null) }
    var clipboardSuggestion by remember { mutableStateOf<SanitizedLink?>(null) }
    var lastDismissedClipboardUrl by remember { mutableStateOf("") }
    var previewLink by remember { mutableStateOf<SanitizedLink?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val historyFlow = remember(selectedFilter) {
        val dao = app.container.downloadHistoryDao
        val source = if (selectedFilter == null) {
            dao.observeAll()
        } else {
            dao.observeByPlatform(selectedFilter!!.key)
        }
        source.map { list -> list.map { it.toDomain() } }
    }
    val historyItems by historyFlow.collectAsState(initial = emptyList())

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clip = readClipboardText(context)
                val sanitized = UrlSanitizer.extractAndSanitize(clip)
                if (sanitized != null &&
                    sanitized.platform != Platform.UNKNOWN &&
                    sanitized.cleanUrl != lastDismissedClipboardUrl
                ) {
                    clipboardSuggestion = sanitized
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun triggerParseFromText(raw: String) {
        val sanitized = UrlSanitizer.extractAndSanitize(raw)
        if (sanitized == null) {
            Toast.makeText(context, "未识别到有效链接（支持抖音、B站、X、Instagram 或 BV 号）", Toast.LENGTH_SHORT).show()
        } else {
            previewLink = sanitized
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_avatar),
                            contentDescription = "MediaSaver",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = "MediaSaver",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            val nextMode = if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                            scope.launch {
                                app.container.settingsDataStore.updateAppearance(themeMode = nextMode)
                            }
                            Toast.makeText(
                                context,
                                if (nextMode == ThemeMode.DARK) "已切换为夜晚模式" else "已切换为浅色模式",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = if (isDarkTheme) "切换为浅色模式" else "切换为夜晚模式",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Clipboard Auto-Detect Banner
            if (clipboardSuggestion != null) {
                val detected = clipboardSuggestion!!
                item(key = "clipboard_banner") {
                    val accent = platformAccent(detected.platform, isDarkTheme)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = accent.container),
                        border = BorderStroke(1.dp, accent.dot.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accent.dot),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "剪贴板检测到 ${detected.platform.displayName} 链接",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accent.onContainer,
                                )
                                Text(
                                    text = detected.cleanUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent.onContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    lastDismissedClipboardUrl = detected.cleanUrl
                                    clipboardSuggestion = null
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "忽略",
                                    tint = accent.onContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Button(
                                onClick = {
                                    inputText = detected.cleanUrl
                                    lastDismissedClipboardUrl = detected.cleanUrl
                                    clipboardSuggestion = null
                                    previewLink = detected
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text("解析")
                            }
                        }
                    }
                }
            }

            // Hero Link Parser Card
            item(key = "paste_bar") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Supported platform accent pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Platform.supportedPlatforms.forEach { platform ->
                                val accent = platformAccent(platform, isDarkTheme)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(accent.dot),
                                    )
                                    Text(
                                        text = if (platform == Platform.X) "X" else platform.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "粘贴分享文案、链接或 B 站 BV 号…",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            trailingIcon = if (inputText.isNotEmpty()) {
                                {
                                    IconButton(onClick = { inputText = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "清空",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val clip = readClipboardText(context)
                                    if (clip.isNullOrBlank()) {
                                        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                    } else {
                                        inputText = clip
                                        triggerParseFromText(clip)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "粘贴并解析",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            Button(
                                enabled = inputText.isNotBlank(),
                                onClick = { triggerParseFromText(inputText) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "立即解析",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            // History Section Header + Filter Chips
            item(key = "history_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "下载记录",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "${historyItems.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (historyItems.isNotEmpty() && selectedFilter == null) {
                            TextButton(
                                onClick = { showClearAllDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "清空",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilter == null,
                                onClick = { selectedFilter = null },
                                shape = RoundedCornerShape(10.dp),
                                label = { Text("全部") },
                            )
                        }
                        items(Platform.supportedPlatforms) { platform ->
                            val accent = platformAccent(platform, isDarkTheme)
                            FilterChip(
                                selected = selectedFilter == platform,
                                onClick = {
                                    selectedFilter = if (selectedFilter == platform) null else platform
                                },
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(accent.dot),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.container,
                                    selectedLabelColor = accent.onContainer,
                                ),
                                label = { Text(if (platform == Platform.X) "X" else platform.displayName) },
                            )
                        }
                    }
                }
            }

            // History List or Polished Empty State
            if (historyItems.isEmpty()) {
                item(key = "empty_history") {
                    EmptyHistoryCard(isFiltered = selectedFilter != null)
                }
            } else {
                items(historyItems, key = { it.id }) { task ->
                    DownloadHistoryCard(
                        task = task,
                        isDarkTheme = isDarkTheme,
                        onOpenGallery = { openInSystemGallery(context, task) },
                        onCopyLink = {
                            copyTextToClipboard(context, "原链接", task.sourceUrl)
                            Toast.makeText(context, "已复制原链接", Toast.LENGTH_SHORT).show()
                        },
                        onCopyError = {
                            copyTextToClipboard(context, "MediaSaver 下载错误日志", task.errorLog)
                            Toast.makeText(context, "已复制完整错误日志", Toast.LENGTH_SHORT).show()
                        },
                        onRetry = {
                            scope.launch {
                                DownloadWorker.enqueue(
                                    context = context,
                                    url = task.sourceUrl,
                                    platform = task.platform,
                                    title = task.title,
                                    author = task.author,
                                    mediaType = task.mediaType,
                                    thumbnailUrl = task.thumbnailUrl,
                                )
                                Toast.makeText(context, "已重新加入下载队列", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                app.container.downloadHistoryDao.deleteById(task.id)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空下载记录？") },
            text = { Text("仅清理应用内的历史记录列表，已保存到手机相册的视频、图片与音频不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        scope.launch {
                            app.container.downloadHistoryDao.clearAll()
                            Toast.makeText(context, "已清空历史记录", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    previewLink?.let { link ->
        ParsePreviewBottomSheet(
            sanitizedLink = link,
            isDarkTheme = isDarkTheme,
            onDismiss = { previewLink = null },
            onEnqueued = {
                previewLink = null
                inputText = ""
            },
        )
    }
}

@Composable
private fun EmptyHistoryCard(isFiltered: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = if (isFiltered) "该平台暂无保存记录" else "还没有保存记录",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "支持视频、高清多图轮播与图文作品，全部在手机端侧直出到系统相册。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!isFiltered) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuidePill(icon = Icons.Rounded.Share, text = "系统分享直达")
                    GuidePill(icon = Icons.Rounded.ContentPaste, text = "复制链接粘贴")
                }
            }
        }
    }
}

@Composable
private fun GuidePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadHistoryCard(
    task: DownloadTask,
    isDarkTheme: Boolean,
    onOpenGallery: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyError: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val canOpen = task.status == DownloadStatus.SUCCESS && task.primaryContentUri.isNotBlank()
    val accent = platformAccent(task.platform, isDarkTheme)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpen, onClick = onOpenGallery),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (task.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = task.thumbnailUrl,
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = task.platform.displayName.take(2),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Bottom-left media type tag
                    Surface(
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = when (task.mediaType) {
                                MediaType.VIDEO -> "视频"
                                MediaType.GALLERY -> "图集"
                                MediaType.MIXED -> "混排"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = accent.container,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = if (task.platform == Platform.X) "X" else task.platform.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent.onContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatHistoryTime(task.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = task.title.ifBlank { task.sourceUrl },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val (statusIcon, statusColor, statusText) = when (task.status) {
                            DownloadStatus.QUEUED -> Triple(
                                Icons.Rounded.Downloading,
                                MaterialTheme.colorScheme.primary,
                                "等待下载…",
                            )
                            DownloadStatus.DOWNLOADING -> Triple(
                                Icons.Rounded.Downloading,
                                MaterialTheme.colorScheme.primary,
                                "下载中 ${task.progress}%",
                            )
                            DownloadStatus.EXPORTING -> Triple(
                                Icons.Rounded.Downloading,
                                MaterialTheme.colorScheme.secondary,
                                "正在写入相册…",
                            )
                            DownloadStatus.SUCCESS -> Triple(
                                Icons.Rounded.CheckCircle,
                                MaterialTheme.colorScheme.tertiary,
                                "已保存 ${task.savedCount} 项到相册",
                            )
                            DownloadStatus.FAILED -> Triple(
                                Icons.Rounded.ErrorOutline,
                                MaterialTheme.colorScheme.error,
                                "失败：${task.errorLog.lineSequence().firstOrNull().orEmpty().take(50)}",
                            )
                        }
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (task.status == DownloadStatus.QUEUED ||
                task.status == DownloadStatus.DOWNLOADING ||
                task.status == DownloadStatus.EXPORTING
            ) {
                LinearProgressIndicator(
                    progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (canOpen) {
                    TextButton(
                        onClick = onOpenGallery,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("相册打开", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (task.status == DownloadStatus.FAILED) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重试", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onCopyError,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("错误日志", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onCopyLink,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = "复制原链接",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun openInSystemGallery(context: Context, task: DownloadTask) {
    if (task.primaryContentUri.isBlank()) return
    val uri = runCatching { Uri.parse(task.primaryContentUri) }.getOrNull() ?: return
    val mime = task.primaryMimeType.ifBlank {
        when (task.mediaType) {
            MediaType.VIDEO -> "video/*"
            MediaType.GALLERY -> "image/*"
            MediaType.MIXED -> "*/*"
        }
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开已保存的媒体"))
    }.onFailure {
        Toast.makeText(context, "未找到可打开该媒体的相册应用", Toast.LENGTH_SHORT).show()
    }
}

private fun formatHistoryTime(epochMillis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
