# MediaSaver

个人自用的纯端侧 Android 媒体保存应用（无后端服务器），支持解析并保存 **抖音、B站、X (Twitter)、Instagram** 的视频、音频、单图、多图轮播与图文作品到手机系统相册。

## 核心特性
- **纯端侧双引擎（Chaquopy + Python 3.12）**：内置 `yt-dlp`、`gallery-dl`、`requests`、`PySocks`，仅编译 `arm64-v8a` 单架构。
- **全平台链接清洗与路由**：从脏分享文案自动提取干净 URL 或 B 站裸 BV 号，剥离追踪参数并自动追踪短链 302 重定向。
- **Android 10+ W^X 合规 FFmpeg 混流**：通过 `jniLibs` + `useLegacyPackaging = true` 加载 `nativeLibraryDir/libffmpeg.so`，支持 B 站 DASH 高清音视频自动混流 MP4；未放置时自动降级单流不崩溃。
- **透明系统分享弹窗**：注册 `ACTION_SEND (text/plain)`，覆层弹出 Material 3 `ModalBottomSheet`，支持视频清晰度选择与 3 列多图网格勾选，关闭即退出不留白屏。
- **后台前台通知下载与相册落盘**：`WorkManager` 实时进度通知，自动按类型写入 `Movies/MediaSaver`、`Pictures/MediaSaver`、`Music/MediaSaver` 并清理临时缓存。
- **分流代理与昼夜主题**：支持 `http://` / `socks5://` 代理与「仅对 X / Instagram 启用代理」分流开关；支持跟随系统 / 浅色 / 夜晚模式一键切换。

## 本地构建

### 1. 环境要求
- JDK 17
- Android SDK（API 35）
- Python 3.12（供 Chaquopy 打包依赖）

### 2. 下载静态 `arm64-v8a` FFmpeg（可选，用于 B 站 DASH 混流）
仓库默认通过 `.gitignore` 忽略 50MB 级二进制文件。首次克隆后可执行：
```bash
curl -fL -o app/src/main/jniLibs/arm64-v8a/libffmpeg.so \
  https://github.com/eugeneware/ffmpeg-static/releases/latest/download/ffmpeg-linux-arm64
```

### 3. 运行测试与编译 APK
```bash
./gradlew testDebugUnitTest assembleDebug
```
产物输出路径：`app/build/outputs/apk/debug/app-debug.apk`
