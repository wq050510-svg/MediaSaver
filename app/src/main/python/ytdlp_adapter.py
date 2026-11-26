import os
import traceback
from http.cookiejar import MozillaCookieJar

import requests
import yt_dlp

MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


def _pick_best_thumbnail(thumbnails):
    if not thumbnails:
        return ""
    # Prefer the largest uncropped original image (usually last in yt-dlp Instagram thumbnails)
    best = None
    best_score = -1
    for idx, t in enumerate(thumbnails):
        url = t.get("url") or ""
        if not url:
            continue
        w = t.get("width") or 0
        h = t.get("height") or 0
        # Penalize cropped/resized Instagram stp thumbnails like s150x150 / c0.240
        is_original = ("_s" not in url and "_p" not in url and "stp=c" not in url)
        score = (w * h) + (10_000_000 if is_original else 0) + idx
        if score > best_score:
            best_score = score
            best = url
    return best or thumbnails[-1].get("url") or ""


def _pick_best_video_url(formats):
    if not formats:
        return "", "mp4"
    # Prefer progressive mp4 with both video and audio
    progressive = [
        f for f in formats
        if f.get("url") and (f.get("vcodec") or "none") != "none" and (f.get("acodec") or "none") != "none"
    ]
    pool = progressive if progressive else [f for f in formats if f.get("url")]
    if not pool:
        return "", "mp4"
    pool.sort(key=lambda f: ((f.get("height") or 0), (f.get("tbr") or 0)))
    chosen = pool[-1]
    return chosen.get("url") or "", chosen.get("ext") or "mp4"


def _extract_instagram_post_raw(url, cookie_file="", proxy=""):
    opts, _ = _build_common_opts(
        platform="INSTAGRAM",
        cookie_file=cookie_file,
        proxy=proxy,
        ffmpeg_path="",
    )
    with yt_dlp.YoutubeDL(opts) as ydl:
        ie = ydl.get_info_extractor("Instagram")
        raw = ie._real_extract(url)
        if not isinstance(raw, dict):
            return None
        entries = list(raw.get("entries") or []) if raw.get("_type") == "playlist" else [raw]
        items = []
        types = set()
        for idx, entry in enumerate(entries):
            if not isinstance(entry, dict):
                continue
            formats = entry.get("formats") or []
            thumbs = entry.get("thumbnails") or []
            best_thumb = _pick_best_thumbnail(thumbs) or entry.get("thumbnail") or ""
            if formats:
                vid_url, ext = _pick_best_video_url(formats)
                if vid_url:
                    types.add("VIDEO")
                    items.append({
                        "url": vid_url,
                        "type": "VIDEO",
                        "thumbnail": best_thumb,
                        "quality": f"视频 #{idx + 1}",
                        "format_id": f"ig_{idx}",
                        "ext": ext,
                    })
                    continue
            if best_thumb:
                types.add("IMAGE")
                items.append({
                    "url": best_thumb,
                    "type": "IMAGE",
                    "thumbnail": best_thumb,
                    "quality": f"原图 #{idx + 1}",
                    "format_id": f"ig_{idx}",
                    "ext": "jpg",
                })

        if not items:
            return None

        title = (raw.get("description") or raw.get("title") or "Instagram 作品").strip()
        if title:
            title = title.splitlines()[0][:120]
        author = (raw.get("uploader") or raw.get("channel") or raw.get("uploader_id") or "").strip()
        if len(types) > 1:
            media_type = "MIXED"
        elif types == {"VIDEO"}:
            media_type = "VIDEO"
        else:
            media_type = "GALLERY"

        return {
            "status": "ok",
            "platform": "INSTAGRAM",
            "title": title,
            "author": author,
            "media_type": media_type,
            "thumbnail": items[0]["thumbnail"],
            "items": items,
            "error_message": "",
        }


def _download_direct_items(items, output_dir, platform, cookie_file="", proxy="", selected_indices=None, progress_callback=None):
    os.makedirs(output_dir, exist_ok=True)
    if selected_indices:
        target_items = [items[i] for i in selected_indices if 0 <= i < len(items)]
        if not target_items:
            target_items = items
    else:
        target_items = items

    session = requests.Session()
    session.headers.update({
        "User-Agent": MOBILE_UA,
        "Referer": "https://www.instagram.com/" if platform == "INSTAGRAM" else "https://x.com/",
    })
    if proxy:
        session.proxies.update({"http": proxy, "https": proxy})
    if cookie_file and os.path.isfile(cookie_file):
        try:
            jar = MozillaCookieJar()
            jar.load(cookie_file, ignore_discard=True, ignore_expires=True)
            session.cookies.update(jar)
        except Exception:
            pass

    total = len(target_items)
    for idx, item in enumerate(target_items):
        resp = session.get(item["url"], stream=True, timeout=30)
        resp.raise_for_status()
        ext = item.get("ext") or ("mp4" if item["type"] == "VIDEO" else "jpg")
        dest = os.path.join(output_dir, f"{platform.lower()}_{idx + 1:03d}.{ext}")
        content_len = int(resp.headers.get("Content-Length") or 0)
        written = 0
        with open(dest, "wb") as fp:
            for chunk in resp.iter_content(chunk_size=65536):
                if not chunk:
                    continue
                fp.write(chunk)
                written += len(chunk)
                if content_len > 0:
                    frac = min(1.0, written / content_len)
                    _emit_progress(progress_callback, 10 + int(((idx + frac) / total) * 85))
        _emit_progress(progress_callback, 10 + int(((idx + 1) / total) * 85))


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


def _build_common_opts(platform, cookie_file="", proxy="", ffmpeg_path=""):
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        )
    }
    if platform == "BILIBILI":
        headers["Referer"] = "https://www.bilibili.com/"
    elif platform == "DOUYIN":
        headers["Referer"] = "https://www.douyin.com/"

    opts = {
        "quiet": True,
        "no_warnings": True,
        "nocolor": True,
        "socket_timeout": 25,
        "retries": 3,
        "fragment_retries": 3,
        "http_headers": headers,
        "noplaylist": True,
        "geo_bypass": True,
    }
    if cookie_file and os.path.isfile(cookie_file):
        opts["cookiefile"] = cookie_file
    if proxy:
        opts["proxy"] = proxy

    has_ffmpeg = bool(ffmpeg_path and os.path.isfile(ffmpeg_path))
    if has_ffmpeg:
        opts["ffmpeg_location"] = ffmpeg_path
    return opts, has_ffmpeg


def _extract_quality_items(info, has_ffmpeg, platform):
    thumbnail = info.get("thumbnail") or ""
    items = []
    seen_labels = set()

    formats = info.get("formats") or []
    # Collect distinct heights for video options
    video_heights = {}
    for fmt in formats:
        vcodec = fmt.get("vcodec") or "none"
        acodec = fmt.get("acodec") or "none"
        height = fmt.get("height") or 0
        fmt_id = fmt.get("format_id") or ""
        if vcodec == "none" or not fmt_id:
            continue
        # If no FFmpeg on Bilibili (DASH), only pick single-stream formats with audio
        if not has_ffmpeg and acodec == "none":
            continue
        if height > 0:
            prev = video_heights.get(height)
            tbr = fmt.get("tbr") or 0
            if prev is None or tbr > (prev.get("tbr") or 0):
                video_heights[height] = fmt

    if has_ffmpeg:
        best_label = "最佳画质 (自动混流 MP4)" if platform == "BILIBILI" else "最佳画质 (推荐)"
        items.append({
            "url": info.get("webpage_url") or info.get("url") or "",
            "type": "VIDEO",
            "thumbnail": thumbnail,
            "quality": best_label,
            "format_id": "bestvideo+bestaudio/best",
            "ext": "mp4",
        })
        seen_labels.add(best_label)
    else:
        best_label = "标准单流 (未检测到 FFmpeg)" if platform == "BILIBILI" else "最佳单流画质"
        items.append({
            "url": info.get("webpage_url") or info.get("url") or "",
            "type": "VIDEO",
            "thumbnail": thumbnail,
            "quality": best_label,
            "format_id": "best[ext=mp4]/best",
            "ext": "mp4",
        })
        seen_labels.add(best_label)

    for height in sorted(video_heights.keys(), reverse=True)[:4]:
        fmt = video_heights[height]
        label = f"{height}p"
        if label in seen_labels:
            continue
        seen_labels.add(label)
        acodec = fmt.get("acodec") or "none"
        fmt_expr = fmt.get("format_id")
        if has_ffmpeg and acodec == "none":
            fmt_expr = f"{fmt_expr}+bestaudio/best"
        items.append({
            "url": fmt.get("url") or info.get("webpage_url") or "",
            "type": "VIDEO",
            "thumbnail": thumbnail,
            "quality": label,
            "format_id": fmt_expr,
            "ext": fmt.get("ext") or "mp4",
        })

    # Add audio-only option if audio streams exist
    audio_formats = [
        f for f in formats
        if (f.get("acodec") or "none") != "none" and (f.get("vcodec") or "none") == "none"
    ]
    if audio_formats:
        items.append({
            "url": info.get("webpage_url") or "",
            "type": "AUDIO",
            "thumbnail": thumbnail,
            "quality": "仅提取音频",
            "format_id": "bestaudio/best",
            "ext": audio_formats[-1].get("ext") or "m4a",
        })

    return items


def parse_media(url, platform="UNKNOWN", cookie_file="", proxy="", ffmpeg_path=""):
    opts, has_ffmpeg = _build_common_opts(
        platform=platform,
        cookie_file=cookie_file,
        proxy=proxy,
        ffmpeg_path=ffmpeg_path,
    )
    opts["skip_download"] = True

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info:
                return {
                    "status": "error",
                    "platform": platform,
                    "title": "",
                    "author": "",
                    "media_type": "VIDEO",
                    "items": [],
                    "error_message": "yt-dlp 未返回有效媒体信息。",
                }

            # If playlist / multi-entry post (e.g., Twitter multi-video tweet), flatten entries
            if info.get("_type") == "playlist" and info.get("entries"):
                entries = [e for e in info["entries"] if e]
                if len(entries) == 1:
                    info = entries[0]
                elif len(entries) > 1:
                    items = []
                    for idx, entry in enumerate(entries):
                        items.append({
                            "url": entry.get("url") or entry.get("webpage_url") or "",
                            "type": "VIDEO",
                            "thumbnail": entry.get("thumbnail") or info.get("thumbnail") or "",
                            "quality": f"视频 #{idx + 1}",
                            "format_id": "best",
                            "ext": entry.get("ext") or "mp4",
                        })
                    return {
                        "status": "ok",
                        "platform": platform,
                        "title": info.get("title") or info.get("description") or "媒体合集",
                        "author": info.get("uploader") or info.get("channel") or "",
                        "media_type": "MIXED",
                        "thumbnail": info.get("thumbnail") or (items[0]["thumbnail"] if items else ""),
                        "items": items,
                        "error_message": "",
                    }

            title = (info.get("title") or info.get("description") or "未命名视频").strip()
            author = (info.get("uploader") or info.get("creator") or info.get("channel") or "").strip()
            thumbnail = info.get("thumbnail") or ""
            items = _extract_quality_items(info, has_ffmpeg=has_ffmpeg, platform=platform)

            return {
                "status": "ok",
                "platform": platform,
                "title": title,
                "author": author,
                "media_type": "VIDEO",
                "thumbnail": thumbnail,
                "items": items,
                "error_message": "",
            }
    except Exception as exc:
        if platform == "INSTAGRAM":
            try:
                ig_result = _extract_instagram_post_raw(
                    url=url,
                    cookie_file=cookie_file,
                    proxy=proxy,
                )
                if ig_result and ig_result.get("items"):
                    return ig_result
            except Exception:
                pass
        return {
            "status": "error",
            "platform": platform,
            "title": "",
            "author": "",
            "media_type": "VIDEO",
            "items": [],
            "error_message": f"{exc}\n{traceback.format_exc()}",
        }


def download_media(
    url,
    output_dir,
    platform="UNKNOWN",
    cookie_file="",
    proxy="",
    ffmpeg_path="",
    format_id="",
    selected_indices=None,
    progress_callback=None,
):
    opts, has_ffmpeg = _build_common_opts(
        platform=platform,
        cookie_file=cookie_file,
        proxy=proxy,
        ffmpeg_path=ffmpeg_path,
    )

    if platform == "INSTAGRAM" and (selected_indices is not None or "/p/" in (url or "").lower()):
        try:
            ig_parsed = _extract_instagram_post_raw(
                url=url,
                cookie_file=cookie_file,
                proxy=proxy,
            )
            if ig_parsed and ig_parsed.get("items"):
                _download_direct_items(
                    items=ig_parsed["items"],
                    output_dir=output_dir,
                    platform="INSTAGRAM",
                    cookie_file=cookie_file,
                    proxy=proxy,
                    selected_indices=selected_indices,
                    progress_callback=progress_callback,
                )
                return {
                    "status": "ok",
                    "platform": "INSTAGRAM",
                    "title": ig_parsed["title"],
                    "author": ig_parsed["author"],
                    "media_type": ig_parsed["media_type"],
                    "items": [],
                    "error_message": "",
                }
        except Exception:
            pass

    if format_id == "bestaudio/best":
        selected_format = "bestaudio/best"
    elif has_ffmpeg:
        selected_format = format_id or "bestvideo+bestaudio/best"
        opts["merge_output_format"] = "mp4"
    else:
        # Strictly degrade to single-stream format when libffmpeg.so is absent
        if format_id and "+" not in format_id:
            selected_format = format_id
        else:
            selected_format = "best[ext=mp4]/best"

    def _progress_hook(status_dict):
        st = status_dict.get("status")
        if st == "downloading":
            total = status_dict.get("total_bytes") or status_dict.get("total_bytes_estimate") or 0
            downloaded = status_dict.get("downloaded_bytes") or 0
            if total > 0:
                pct = 5 + int((downloaded / total) * 85)
                _emit_progress(progress_callback, pct)
        elif st == "finished":
            _emit_progress(progress_callback, 92)

    opts.update({
        "format": selected_format,
        "outtmpl": os.path.join(output_dir, "%(id)s_%(autonumber)s.%(ext)s"),
        "progress_hooks": [_progress_hook],
        "restrictfilenames": True,
        "windowsfilenames": True,
    })

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            title = ""
            author = ""
            if isinstance(info, dict):
                title = (info.get("title") or info.get("description") or "").strip()
                author = (info.get("uploader") or info.get("creator") or info.get("channel") or "").strip()
            return {
                "status": "ok",
                "platform": platform,
                "title": title,
                "author": author,
                "media_type": "VIDEO",
                "items": [],
                "error_message": "",
            }
    except Exception as exc:
        if platform == "INSTAGRAM":
            try:
                ig_parsed = _extract_instagram_post_raw(
                    url=url,
                    cookie_file=cookie_file,
                    proxy=proxy,
                )
                if ig_parsed and ig_parsed.get("items"):
                    _download_direct_items(
                        items=ig_parsed["items"],
                        output_dir=output_dir,
                        platform="INSTAGRAM",
                        cookie_file=cookie_file,
                        proxy=proxy,
                        selected_indices=selected_indices,
                        progress_callback=progress_callback,
                    )
                    return {
                        "status": "ok",
                        "platform": "INSTAGRAM",
                        "title": ig_parsed["title"],
                        "author": ig_parsed["author"],
                        "media_type": ig_parsed["media_type"],
                        "items": [],
                        "error_message": "",
                    }
            except Exception:
                pass
        return {
            "status": "error",
            "platform": platform,
            "title": "",
            "author": "",
            "media_type": "VIDEO",
            "items": [],
            "error_message": f"{exc}\n{traceback.format_exc()}",
        }
