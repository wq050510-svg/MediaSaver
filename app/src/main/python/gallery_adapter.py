import io
import os
import traceback
from http.cookiejar import MozillaCookieJar
from urllib.parse import urlparse

import gallery_dl
import gallery_dl.config as gdl_config
import gallery_dl.job as gdl_job
import requests

VIDEO_EXTS = {"mp4", "webm", "mov", "mkv", "m4v", "ts"}
AUDIO_EXTS = {"mp3", "m4a", "aac", "opus", "ogg", "wav"}

MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


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


def _configure_gdl(cookie_file="", proxy="", output_dir=None):
    gdl_config.clear()
    gdl_config.set(("extractor",), "timeout", 25.0)
    gdl_config.set(("extractor",), "retries", 3)
    if cookie_file and os.path.isfile(cookie_file):
        gdl_config.set(("extractor",), "cookies", cookie_file)
    if proxy:
        gdl_config.set(("extractor",), "proxy", proxy)
    if output_dir:
        gdl_config.set(("extractor",), "base-directory", output_dir)
        gdl_config.set(("extractor",), "directory", [""])
        gdl_config.set(("extractor",), "filename", "{num:>03}_{filename}.{extension}")
    # Enable videos on twitter & instagram
    gdl_config.set(("extractor", "twitter"), "videos", True)
    gdl_config.set(("extractor", "twitter"), "retweets", False)
    gdl_config.set(("extractor", "instagram"), "videos", True)


def _guess_item_type(media_url, kwdict):
    ext = (kwdict.get("extension") or "").lower().lstrip(".")
    if not ext:
        path = urlparse(media_url).path
        ext = os.path.splitext(path)[1].lower().lstrip(".")
    if ext in VIDEO_EXTS:
        return "VIDEO", ext or "mp4"
    if ext in AUDIO_EXTS:
        return "AUDIO", ext or "m4a"
    return "IMAGE", ext or "jpg"


def _extract_entries(url, cookie_file="", proxy=""):
    _configure_gdl(cookie_file=cookie_file, proxy=proxy)
    buffer = io.StringIO()
    job = gdl_job.DataJob(url, file=buffer)
    job.run()

    meta = {}
    items = []
    errors = []
    for entry in job.data:
        if not isinstance(entry, tuple) or len(entry) < 2:
            continue
        code = entry[0]
        # Error entry in gallery-dl DataJob uses code == -1
        if code == -1 and isinstance(entry[1], dict):
            err_type = entry[1].get("error") or "Error"
            err_msg = entry[1].get("message") or str(entry[1])
            errors.append(f"{err_type}: {err_msg}")
        # Message.Directory == 2, Message.Url == 3 in gallery_dl
        elif code == 2 and isinstance(entry[1], dict):
            meta.update(entry[1])
        elif code == 3 and len(entry) >= 3:
            media_url = entry[1]
            kwdict = entry[2] if isinstance(entry[2], dict) else {}
            if not meta:
                meta.update(kwdict)
            item_type, ext = _guess_item_type(media_url, kwdict)
            width = kwdict.get("width")
            height = kwdict.get("height")
            quality = f"{width}x{height}" if width and height else ext.upper()
            thumb = (
                kwdict.get("thumbnail")
                or kwdict.get("preview")
                or (media_url if item_type == "IMAGE" else "")
            )
            items.append({
                "url": media_url,
                "type": item_type,
                "thumbnail": thumb,
                "quality": quality,
                "format_id": str(len(items)),
                "ext": ext,
                "_headers": kwdict.get("_http_headers") or {},
            })

    if not items and errors:
        raise RuntimeError("; ".join(errors))

    title = (
        meta.get("description")
        or meta.get("content")
        or meta.get("title")
        or meta.get("post_id")
        or meta.get("tweet_id")
        or "图文帖子"
    )
    if isinstance(title, str):
        title = title.strip().splitlines()[0][:120] if title.strip() else "图文帖子"
    else:
        title = str(title)

    author_obj = meta.get("author") or meta.get("user") or {}
    if isinstance(author_obj, dict):
        author = (
            author_obj.get("nick")
            or author_obj.get("name")
            or author_obj.get("username")
            or author_obj.get("fullname")
            or ""
        )
    else:
        author = str(meta.get("username") or meta.get("fullname") or author_obj or "")

    return title, author.strip(), items


def parse_gallery(url, platform="UNKNOWN", cookie_file="", proxy=""):
    try:
        title, author, raw_items = _extract_entries(url, cookie_file=cookie_file, proxy=proxy)
        if not raw_items:
            return {
                "status": "error",
                "platform": platform,
                "title": title,
                "author": author,
                "media_type": "GALLERY",
                "items": [],
                "error_message": "gallery-dl 未提取到任何媒体项。",
            }

        clean_items = []
        types = set()
        for item in raw_items:
            types.add(item["type"])
            clean_items.append({
                "url": item["url"],
                "type": item["type"],
                "thumbnail": item["thumbnail"],
                "quality": item["quality"],
                "format_id": item["format_id"],
                "ext": item["ext"],
            })

        if len(types) > 1:
            media_type = "MIXED"
        elif types == {"VIDEO"}:
            media_type = "VIDEO"
        else:
            media_type = "GALLERY"

        cover = next((i["thumbnail"] for i in clean_items if i["thumbnail"]), "")
        return {
            "status": "ok",
            "platform": platform,
            "title": title,
            "author": author,
            "media_type": media_type,
            "thumbnail": cover,
            "items": clean_items,
            "error_message": "",
        }
    except Exception as exc:
        return {
            "status": "error",
            "platform": platform,
            "title": "",
            "author": "",
            "media_type": "GALLERY",
            "items": [],
            "error_message": f"{exc}\n{traceback.format_exc()}",
        }


def download_gallery(
    url,
    output_dir,
    platform="UNKNOWN",
    cookie_file="",
    proxy="",
    selected_indices=None,
    progress_callback=None,
):
    os.makedirs(output_dir, exist_ok=True)
    try:
        title, author, raw_items = _extract_entries(url, cookie_file=cookie_file, proxy=proxy)
        if not raw_items:
            # Fallback directly to DownloadJob
            _configure_gdl(cookie_file=cookie_file, proxy=proxy, output_dir=output_dir)
            gdl_job.DownloadJob(url).run()
            return {
                "status": "ok",
                "platform": platform,
                "title": title or "媒体作品",
                "author": author,
                "media_type": "GALLERY",
                "items": [],
                "error_message": "",
            }

        if selected_indices:
            target_items = [
                raw_items[i] for i in selected_indices if 0 <= i < len(raw_items)
            ]
            if not target_items:
                target_items = raw_items
        else:
            target_items = raw_items

        session = requests.Session()
        session.headers.update({"User-Agent": MOBILE_UA})
        if platform == "INSTAGRAM":
            session.headers.update({"Referer": "https://www.instagram.com/"})
        elif platform == "X":
            session.headers.update({"Referer": "https://x.com/"})
        elif platform == "BILIBILI":
            session.headers.update({"Referer": "https://www.bilibili.com/"})

        if proxy:
            session.proxies.update({"http": proxy, "https": proxy})
        if cookie_file and os.path.isfile(cookie_file):
            try:
                jar = MozillaCookieJar()
                jar.load(cookie_file, ignore_discard=True, ignore_expires=True)
                session.cookies.update(jar)
            except Exception:
                pass

        total_items = len(target_items)
        downloaded_count = 0
        for idx, item in enumerate(target_items):
            media_url = item["url"]
            ext = item.get("ext") or ("mp4" if item["type"] == "VIDEO" else "jpg")
            filename = f"{platform.lower()}_{idx + 1:03d}.{ext}"
            dest_path = os.path.join(output_dir, filename)
            req_headers = dict(item.get("_headers") or {})

            resp = session.get(media_url, headers=req_headers, stream=True, timeout=30)
            resp.raise_for_status()
            content_len = int(resp.headers.get("Content-Length") or 0)
            written = 0
            with open(dest_path, "wb") as fp:
                for chunk in resp.iter_content(chunk_size=65536):
                    if not chunk:
                        continue
                    fp.write(chunk)
                    written += len(chunk)
                    if content_len > 0:
                        item_frac = min(1.0, written / content_len)
                        overall = 10 + int(((idx + item_frac) / total_items) * 85)
                        _emit_progress(progress_callback, overall)

            downloaded_count += 1
            overall = 10 + int((downloaded_count / total_items) * 85)
            _emit_progress(progress_callback, overall)

        types = {i["type"] for i in target_items}
        media_type = "MIXED" if len(types) > 1 else ("VIDEO" if types == {"VIDEO"} else "GALLERY")
        return {
            "status": "ok",
            "platform": platform,
            "title": title,
            "author": author,
            "media_type": media_type,
            "items": [],
            "error_message": "",
        }
    except Exception as exc:
        return {
            "status": "error",
            "platform": platform,
            "title": "",
            "author": "",
            "media_type": "GALLERY",
            "items": [],
            "error_message": f"{exc}\n{traceback.format_exc()}",
        }
