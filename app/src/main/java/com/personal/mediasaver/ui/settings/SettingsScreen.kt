package com.personal.mediasaver.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.mediasaver.MediaSaverApp
import com.personal.mediasaver.domain.model.AppearanceSettings
import com.personal.mediasaver.domain.model.EngineHealth
import com.personal.mediasaver.domain.model.ProxySettings
import com.personal.mediasaver.domain.model.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaSaverApp
    val scope = rememberCoroutineScope()

    val appearance by app.container.settingsDataStore.appearanceSettingsFlow
        .collectAsState(initial = AppearanceSettings())
    val proxySettings by app.container.settingsDataStore.proxySettingsFlow
        .collectAsState(initial = ProxySettings())

    var proxyInput by remember { mutableStateOf("") }
    var proxyInputInitialized by remember { mutableStateOf(false) }
    var engineHealth by remember { mutableStateOf<EngineHealth?>(null) }
    var checkingHealth by remember { mutableStateOf(true) }
    var healthRefreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(proxySettings.proxyUrl) {
        if (!proxyInputInitialized) {
            proxyInput = proxySettings.proxyUrl
            proxyInputInitialized = true
        }
    }

    LaunchedEffect(healthRefreshTick) {
        checkingHealth = true
        engineHealth = app.container.engineBridge.checkHealth()
        checkingHealth = false
    }

    val isProxyValid = proxyInput.isBlank() || ProxySettings.isValidProxyUrl(proxyInput)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("设置与诊断", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Appearance & Night Mode Card
            SettingsCard {
                SectionTitle(icon = Icons.Rounded.DarkMode, title = "外观与夜晚模式")

                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            selected = appearance.themeMode == mode,
                            onClick = {
                                scope.launch {
                                    app.container.settingsDataStore.updateAppearance(themeMode = mode)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = mode.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("跟随系统动态取色", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "关闭以使用定制靛蓝护眼配色，开启则提取壁纸主题色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = appearance.dynamicColor,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    app.container.settingsDataStore.updateAppearance(dynamicColor = enabled)
                                }
                            },
                        )
                    }
                }
            }

            // 2. Proxy & Split-Tunnel Card
            SettingsCard {
                SectionTitle(icon = Icons.Rounded.VpnKey, title = "代理与分流设置")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("启用网络代理", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "透传至 requests / yt-dlp / gallery-dl 端侧引擎",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = proxySettings.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                app.container.settingsDataStore.updateProxySettings(enabled = enabled)
                            }
                        },
                    )
                }

                OutlinedTextField(
                    value = proxyInput,
                    onValueChange = { proxyInput = it },
                    label = { Text("代理地址") },
                    placeholder = { Text("http://127.0.0.1:7890") },
                    singleLine = true,
                    isError = !isProxyValid,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    supportingText = {
                        if (!isProxyValid) {
                            Text("格式无效，请输入 http:// 或 socks5:// 开头加端口")
                        } else {
                            Text("支持 http:// 与 socks5://，例如 socks5://127.0.0.1:10808")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        enabled = proxyInput.isNotBlank() && ProxySettings.isValidProxyUrl(proxyInput),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            scope.launch {
                                app.container.settingsDataStore.updateProxySettings(proxyUrl = proxyInput.trim())
                                Toast.makeText(context, "代理地址已保存", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text("保存代理地址")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("仅对 X / Instagram 启用代理", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启后抖音与 B 站保持直连，仅海外平台走代理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = proxySettings.onlyOverseas,
                        onCheckedChange = { onlyOverseas ->
                            scope.launch {
                                app.container.settingsDataStore.updateProxySettings(onlyOverseas = onlyOverseas)
                            }
                        },
                    )
                }
            }

            // 3. Engine Diagnostics & FFmpeg Card
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionTitle(icon = Icons.Rounded.Memory, title = "端侧引擎与 FFmpeg 检测")
                    IconButton(onClick = { healthRefreshTick++ }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "重新检测",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (checkingHealth) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在检测 Python 双引擎与 FFmpeg…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val health = engineHealth
                    if (health != null) {
                        // Compact 2x2 version grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            EngineMetricTile("Python", health.pythonVersion, Modifier.weight(1f))
                            EngineMetricTile("yt-dlp", health.ytDlpVersion, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            EngineMetricTile("gallery-dl", health.galleryDlVersion, Modifier.weight(1f))
                            EngineMetricTile("requests", health.requestsVersion, Modifier.weight(1f))
                        }

                        // FFmpeg Status Banner
                        val bannerContainer = if (health.ffmpegAvailable) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                        val bannerContent = if (health.ffmpegAvailable) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                        val bannerIconTint = if (health.ffmpegAvailable) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }

                        Surface(
                            color = bannerContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = if (health.ffmpegAvailable) {
                                        Icons.Rounded.CheckCircle
                                    } else {
                                        Icons.Rounded.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = bannerIconTint,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (health.ffmpegAvailable) {
                                            "FFmpeg 已就绪 · B 站 DASH 混流可用"
                                        } else {
                                            "未检测到 FFmpeg"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = bannerContent,
                                    )
                                    Text(
                                        text = if (health.ffmpegAvailable) {
                                            health.ffmpegPath
                                        } else {
                                            "B 站下载将自动降级为单流格式。放入 arm64-v8a 版 libffmpeg.so 后重新打包即可启用 1080P+ 混流。"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = bannerContent.copy(alpha = 0.85f),
                                        fontFamily = if (health.ffmpegAvailable) FontFamily.Monospace else FontFamily.Default,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
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
            content()
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun EngineMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
