import json
import mimetypes
import os
import sys
import traceback
from http.cookiejar import MozillaCookieJar
from urllib.parse import urlparse

import requests

import douyin_note
import gallery_adapter
import ytdlp_adapter

MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)

SHORT_LINK_HOSTS = {
    "v.douyin.com",
    "b23.tv",
    "bili2233.cn",
    "t.co",
    "instagr.am",
}

VIDEO_EXTENSIONS = {".mp4", ".mkv", ".webm", ".mov", ".m4v", ".flv", ".ts"}
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".avif"}
AUDIO_EXTENSIONS = {".mp3", ".m4a", ".aac", ".opus", ".ogg", ".wav", ".flac"}


def _emit_progress(callback, percent):
    if callback is None:
        return
    try:
        clamped = max(0, min(100, int(percent)))
        if hasattr(callback, "onProgress"):
            callback.onProgress(clamped)
        elif callable(callback):
            callback(clamped)
    except Exception:
        pass


def _detect_platform(url):
    try:
        host = (urlparse(url).hostname or "").lower()
    except Exception:
        host = ""
    if host == "douyin.com" or host.endswith(".douyin.com") or host == "iesdouyin.com" or host.endswith(".iesdouyin.com"):
        return "DOUYIN"
    if host == "bilibili.com" or host.endswith(".bilibili.com") or host == "b23.tv" or host.endswith(".b23.tv") or host.endswith(".bili2233.cn"):
        return "BILIBILI"
    if host in ("x.com", "twitter.com", "t.co") or host.endswith(".x.com") or host.endswith(".twitter.com"):
        return "X"
    if host in ("instagram.com", "instagr.am") or host.endswith(".instagram.com"):
        return "INSTAGRAM"
    return "UNKNOWN"


def _build_session(cookie_file="", proxy=""):
    session = requests.Session()
    session.headers.update({"User-Agent": MOBILE_UA})
    if proxy:
        session.proxies.update({"http": proxy, "https": proxy})
    if cookie_file and os.path.isfile(cookie_file):
        try:
            jar = MozillaCookieJar()
            jar.load(cookie_file, ignore_discard=True, ignore_expires=True)
            session.cookies.update(jar)
        except Exception:
            pass
    return session


def _resolve_short_url(url, cookie_file="", proxy=""):
    try:
        host = (urlparse(url).hostname or "").lower()
        if host not in SHORT_LINK_HOSTS and not any(host.endswith("." + h) for h in SHORT_LINK_HOSTS):
            return url
        session = _build_session(cookie_file=cookie_file, proxy=proxy)
        resp = session.get(url, allow_redirects=True, timeout=15, stream=True)
        final_url = resp.url or url
        resp.close()
        return final_url
    except Exception:
        return url


def _is_douyin_note_url(url):
    lower = (url or "").lower()
    return "/note/" in lower or "/slides/" in lower or "share/note/" in lower


def _is_bilibili_dynamic_url(url):
    lower = (url or "").lower()
    return "/opus/" in lower or "t.bilibili.com" in lower or "/dynamic/" in lower


def _classify_file(path):
    ext = os.path.splitext(path)[1].lower()
    if ext in VIDEO_EXTENSIONS:
        mime = mimetypes.guess_type(path)[0] or "video/mp4"
        return "VIDEO", mime
    if ext in IMAGE_EXTENSIONS:
        mime = mimetypes.guess_type(path)[0] or "image/jpeg"
        return "IMAGE", mime
    if ext in AUDIO_EXTENSIONS:
        mime = mimetypes.guess_type(path)[0] or "audio/mp4"
        return "AUDIO", mime
    mime = mimetypes.guess_type(path)[0] or "application/octet-stream"
    if mime.startswith("video/"):
        return "VIDEO", mime
    if mime.startswith("image/"):
        return "IMAGE", mime
    if mime.startswith("audio/"):
        return "AUDIO", mime
    return "VIDEO", "video/mp4"


def _scan_output_files(output_dir):
    results = []
    if not output_dir or not os.path.isdir(output_dir):
        return results
    for root, _, filenames in os.walk(output_dir):
        for name in sorted(filenames):
            lower = name.lower()
            if lower.endswith((".part", ".ytdl", ".temp", ".tmp", ".json", ".txt")):
                continue
            full_path = os.path.join(root, name)
            try:
                if os.path.isfile(full_path) and os.path.getsize(full_path) > 0:
                    media_type, mime_type = _classify_file(full_path)
                    results.append({
                        "path": full_path,
                        "type": media_type,
                        "mime_type": mime_type,
                    })
            except OSError:
                continue
    return results


def _error_response(platform, message):
    return json.dumps({
        "status": "error",
        "platform": platform or "UNKNOWN",
        "title": "",
        "author": "",
        "media_type": "VIDEO",
        "items": [],
        "files": [],
        "error_message": str(message),
    }, ensure_ascii=False)


def health_check(ffmpeg_path=""):
    python_ver = sys.version.split()[0]
    yt_dlp_ver = "未安装"
    gallery_dl_ver = "未安装"
    requests_ver = getattr(requests, "__version__", "Unknown")

    try:
        import yt_dlp.version
        yt_dlp_ver = yt_dlp.version.__version__
    except Exception as exc:
        yt_dlp_ver = f"异常: {exc}"

    try:
        import gallery_dl.version
        gallery_dl_ver = gallery_dl.version.__version__
    except Exception as exc:
        gallery_dl_ver = f"异常: {exc}"

    ffmpeg_ok = bool(ffmpeg_path and os.path.isfile(ffmpeg_path))
    return json.dumps({
        "status": "ok",
        "python_version": python_ver,
        "yt_dlp_version": yt_dlp_ver,
        "gallery_dl_version": gallery_dl_ver,
        "requests_version": requests_ver,
        "ffmpeg_available": ffmpeg_ok,
        "ffmpeg_path": ffmpeg_path if ffmpeg_ok else "",
        "error_message": "",
    }, ensure_ascii=False)


def parse(url, platform="", cookie_file="", proxy="", ffmpeg_path=""):
    resolved_platform = (platform or _detect_platform(url)).upper()
    try:
        resolved_url = _resolve_short_url(url, cookie_file=cookie_file, proxy=proxy)
        detected_after = _detect_platform(resolved_url)
        if detected_after != "UNKNOWN":
            resolved_platform = detected_after

        result = None
        errors = []

        if resolved_platform in ("X", "INSTAGRAM"):
            try:
                result = gallery_adapter.parse_gallery(
                    url=resolved_url,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                )
            except Exception as exc:
                errors.append(f"gallery-dl: {exc}")

            if not result or result.get("status") != "ok" or not result.get("items"):
                if result and result.get("error_message"):
                    errors.append(f"gallery-dl: {result['error_message']}")
                try:
                    fallback = ytdlp_adapter.parse_media(
                        url=resolved_url,
                        platform=resolved_platform,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        ffmpeg_path=ffmpeg_path,
                    )
                    if fallback.get("status") == "ok":
                        result = fallback
                    else:
                        errors.append(f"yt-dlp: {fallback.get('error_message', '解析失败')}")
                except Exception as exc:
                    errors.append(f"yt-dlp: {exc}")

        elif resolved_platform == "DOUYIN":
            if _is_douyin_note_url(resolved_url):
                result = douyin_note.parse_note(
                    url=resolved_url,
                    cookie_file=cookie_file,
                    proxy=proxy,
                )
                if result.get("status") != "ok":
                    errors.append(f"douyin_note: {result.get('error_message', '')}")
                    fallback = ytdlp_adapter.parse_media(
                        url=resolved_url,
                        platform=resolved_platform,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        ffmpeg_path=ffmpeg_path,
                    )
                    if fallback.get("status") == "ok":
                        result = fallback
                    else:
                        errors.append(f"yt-dlp: {fallback.get('error_message', '')}")
            else:
                result = ytdlp_adapter.parse_media(
                    url=resolved_url,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    ffmpeg_path=ffmpeg_path,
                )
                if result.get("status") != "ok":
                    errors.append(f"yt-dlp: {result.get('error_message', '')}")
                    note_fallback = douyin_note.parse_note(
                        url=resolved_url,
                        cookie_file=cookie_file,
                        proxy=proxy,
                    )
                    if note_fallback.get("status") == "ok":
                        result = note_fallback
                    else:
                        errors.append(f"douyin_note: {note_fallback.get('error_message', '')}")

        elif resolved_platform == "BILIBILI":
            if _is_bilibili_dynamic_url(resolved_url):
                result = gallery_adapter.parse_gallery(
                    url=resolved_url,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                )
                if result.get("status") != "ok" or not result.get("items"):
                    errors.append(f"gallery-dl: {result.get('error_message', '')}")
                    fallback = ytdlp_adapter.parse_media(
                        url=resolved_url,
                        platform=resolved_platform,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        ffmpeg_path=ffmpeg_path,
                    )
                    if fallback.get("status") == "ok":
                        result = fallback
                    else:
                        errors.append(f"yt-dlp: {fallback.get('error_message', '')}")
            else:
                result = ytdlp_adapter.parse_media(
                    url=resolved_url,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    ffmpeg_path=ffmpeg_path,
                )
        else:
            result = ytdlp_adapter.parse_media(
                url=resolved_url,
                platform=resolved_platform,
                cookie_file=cookie_file,
                proxy=proxy,
                ffmpeg_path=ffmpeg_path,
            )

        if not result or result.get("status") != "ok":
            err_msg = " | ".join(e for e in errors if e) or (result or {}).get("error_message") or "解析失败，请检查链接或网络代理设置。"
            return _error_response(resolved_platform, err_msg)

        result["platform"] = resolved_platform
        result["resolved_url"] = resolved_url
        return json.dumps(result, ensure_ascii=False)
    except Exception:
        return _error_response(resolved_platform, traceback.format_exc())


def download(
    url,
    output_dir,
    platform="",
    cookie_file="",
    proxy="",
    ffmpeg_path="",
    format_id="",
    selected_indices_json="",
    progress_callback=None,
):
    resolved_platform = (platform or _detect_platform(url)).upper()
    os.makedirs(output_dir, exist_ok=True)
    _emit_progress(progress_callback, 2)

    selected_indices = None
    if selected_indices_json:
        try:
            parsed_idx = json.loads(selected_indices_json)
            if isinstance(parsed_idx, list) and len(parsed_idx) > 0:
                selected_indices = [int(i) for i in parsed_idx]
        except Exception:
            selected_indices = None

    try:
        resolved_url = _resolve_short_url(url, cookie_file=cookie_file, proxy=proxy)
        detected_after = _detect_platform(resolved_url)
        if detected_after != "UNKNOWN":
            resolved_platform = detected_after

        _emit_progress(progress_callback, 5)
        result = None
        errors = []

        if resolved_platform in ("X", "INSTAGRAM"):
            try:
                result = gallery_adapter.download_gallery(
                    url=resolved_url,
                    output_dir=output_dir,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    selected_indices=selected_indices,
                    progress_callback=progress_callback,
                )
            except Exception as exc:
                errors.append(f"gallery-dl: {exc}")

            files = _scan_output_files(output_dir)
            if not result or result.get("status") != "ok" or not files:
                if result and result.get("error_message"):
                    errors.append(f"gallery-dl: {result['error_message']}")
                try:
                    result = ytdlp_adapter.download_media(
                        url=resolved_url,
                        output_dir=output_dir,
                        platform=resolved_platform,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        ffmpeg_path=ffmpeg_path,
                        format_id=format_id,
                        selected_indices=selected_indices,
                        progress_callback=progress_callback,
                    )
                except Exception as exc:
                    errors.append(f"yt-dlp: {exc}")

        elif resolved_platform == "DOUYIN":
            if _is_douyin_note_url(resolved_url):
                result = douyin_note.download_note(
                    url=resolved_url,
                    output_dir=output_dir,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    selected_indices=selected_indices,
                    progress_callback=progress_callback,
                )
                if result.get("status") != "ok" or not _scan_output_files(output_dir):
                    errors.append(f"douyin_note: {result.get('error_message', '')}")
                    result = ytdlp_adapter.download_media(
                        url=resolved_url,
                        output_dir=output_dir,
                        platform=resolved_platform,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        ffmpeg_path=ffmpeg_path,
                        format_id=format_id,
                        selected_indices=selected_indices,
                        progress_callback=progress_callback,
                    )
            else:
                result = ytdlp_adapter.download_media(
                    url=resolved_url,
                    output_dir=output_dir,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    ffmpeg_path=ffmpeg_path,
                    format_id=format_id,
                    progress_callback=progress_callback,
                )
                if result.get("status") != "ok" or not _scan_output_files(output_dir):
                    errors.append(f"yt-dlp: {result.get('error_message', '')}")
                    note_res = douyin_note.download_note(
                        url=resolved_url,
                        output_dir=output_dir,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        selected_indices=selected_indices,
                        progress_callback=progress_callback,
                    )
                    if note_res.get("status") == "ok" and _scan_output_files(output_dir):
                        result = note_res
                    else:
                        errors.append(f"douyin_note: {note_res.get('error_message', '')}")

        elif resolved_platform == "BILIBILI":
            if _is_bilibili_dynamic_url(resolved_url):
                result = gallery_adapter.download_gallery(
                    url=resolved_url,
                    output_dir=output_dir,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    selected_indices=selected_indices,
                    progress_callback=progress_callback,
                )
                if result.get("status") != "ok" or not _scan_output_files(output_dir):
                    errors.append(f"gallery-dl: {result.get('error_message', '')}")
                    result = ytdlp_adapter.download_media(
                        url=resolved_url,
                        output_dir=output_dir,
                        platform=resolved_platform,
                        cookie_file=cookie_file,
                        proxy=proxy,
                        ffmpeg_path=ffmpeg_path,
                        format_id=format_id,
                        selected_indices=selected_indices,
                        progress_callback=progress_callback,
                    )
            else:
                result = ytdlp_adapter.download_media(
                    url=resolved_url,
                    output_dir=output_dir,
                    platform=resolved_platform,
                    cookie_file=cookie_file,
                    proxy=proxy,
                    ffmpeg_path=ffmpeg_path,
                    format_id=format_id,
                    progress_callback=progress_callback,
                )
        else:
            result = ytdlp_adapter.download_media(
                url=resolved_url,
                output_dir=output_dir,
                platform=resolved_platform,
                cookie_file=cookie_file,
                proxy=proxy,
                ffmpeg_path=ffmpeg_path,
                format_id=format_id,
                progress_callback=progress_callback,
            )

        files = _scan_output_files(output_dir)
        if not files:
            err_msg = " | ".join(e for e in errors if e) or (result or {}).get("error_message") or "未获取到可保存的媒体文件。"
            return _error_response(resolved_platform, err_msg)

        _emit_progress(progress_callback, 100)
        final_media_type = (result or {}).get("media_type", "VIDEO")
        file_types = {f["type"] for f in files}
        if len(file_types) > 1:
            final_media_type = "MIXED"
        elif file_types == {"IMAGE"}:
            final_media_type = "GALLERY"

        return json.dumps({
            "status": "ok",
            "platform": resolved_platform,
            "title": (result or {}).get("title", ""),
            "author": (result or {}).get("author", ""),
            "media_type": final_media_type,
            "items": (result or {}).get("items", []),
            "files": files,
            "error_message": "",
        }, ensure_ascii=False)
    except Exception:
        return _error_response(resolved_platform, traceback.format_exc())
