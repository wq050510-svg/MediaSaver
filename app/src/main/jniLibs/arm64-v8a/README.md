# FFmpeg 二进制放置说明（Android 10+ W^X 合规）

请将适用于 `arm64-v8a`（aarch64-linux-android）架构的 FFmpeg 可执行文件重命名为 `libffmpeg.so` 后放置于本目录：

    app/src/main/jniLibs/arm64-v8a/libffmpeg.so

说明：
1. 本项目已在 `app/build.gradle.kts` 中开启 `packaging { jniLibs { useLegacyPackaging = true } }`，确保安装时系统将该文件解压到 `context.applicationInfo.nativeLibraryDir/libffmpeg.so` 并赋予合法可执行权限。
2. 应用运行时会自动检测 `${context.applicationInfo.nativeLibraryDir}/libffmpeg.so`：
   - 存在时：作为 `ffmpeg_location` 传递给 `yt-dlp`，自动执行 B 站 DASH 高清音视频混流（`bestvideo+bestaudio/best` -> MP4）。
   - 不存在时：在设置页提示“未检测到 FFmpeg”，B 站下载自动降级为单流格式（`best`），不会崩溃。
3. 严禁将 `ffmpeg` 可执行文件放入 `filesDir` 或 `cacheDir` 运行。
