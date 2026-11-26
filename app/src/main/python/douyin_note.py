import json
import os
import re
import traceback
from http.cookiejar import MozillaCookieJar
from urllib.parse import unquote

import requests

IOS_SAFARI_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
)

AWEME_ID_PATTERNS = [
    re.compile(r"/note/(\d+)"),
    re.compile(r"/slides/(\d+)"),
    re.compile(r"/video/(\d+)"),
    re.compile(r"modal_id=(\d+)"),
    re.compile(r"item_ids=(\d+)"),
]


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


def _extract_aweme_id(url):
    for pattern in AWEME_ID_PATTERNS:
        m = pattern.search(url or "")
        if m:
            return m.group(1)
    return None


def _build_session(cookie_file="", proxy=""):
    session = requests.Session()
    session.headers.update({
        "User-Agent": IOS_SAFARI_UA,
        "Referer": "https://www.douyin.com/",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
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
    return session


def _find_aweme_detail(obj):
    if isinstance(obj, dict):
        if "images" in obj and isinstance(obj.get("images"), list) and obj.get("images"):
            return obj
        if "aweme_detail" in obj and isinstance(obj["aweme_detail"], dict):
            return obj["aweme_detail"]
        if "item_list" in obj and isinstance(obj["item_list"], list) and obj["item_list"]:
            first = obj["item_list"][0]
            if isinstance(first, dict):
                return first
        for val in obj.values():
            found = _find_aweme_detail(val)
            if found:
                return found
    elif isinstance(obj, list):
        for item in obj:
            found = _find_aweme_detail(item)
            if found:
                return found
    return None


def _extract_json_from_html(html):
    # Pattern 1: window._ROUTER_DATA = {...}</script>
    router_match = re.search(
        r"window\._ROUTER_DATA\s*=\s*(\{.+?\})\s*;?\s*</script>",
        html,
        flags=re.DOTALL,
    )
    if router_match:
        try:
            data = json.loads(router_match.group(1))
            detail = _find_aweme_detail(data)
            if detail:
                return detail
        except Exception:
            pass

    # Pattern 2: <script id="RENDER_DATA" type="application/json">URL_ENCODED</script>
    render_match = re.search(
        r'<script[^>]+id="RENDER_DATA"[^>]*>(.*?)</script>',
        html,
        flags=re.DOTALL,
    )
    if render_match:
        try:
            decoded = unquote(render_match.group(1).strip())
            data = json.loads(decoded)
            detail = _find_aweme_detail(data)
            if detail:
                return detail
        except Exception:
            pass

    return None


def _pick_best_image_url(image_obj):
    url_list = image_obj.get("url_list") or image_obj.get("download_url_list") or []
    if not url_list:
        return ""
    # Prefer jpeg/png over webp/heic when available for maximum gallery compatibility
    for u in url_list:
        lower = u.lower()
        if ".jpeg" in lower or ".jpg" in lower or ".png" in lower:
            return u
    return url_list[0]


def _extract_note_data(url, cookie_file="", proxy=""):
    session = _build_session(cookie_file=cookie_file, proxy=proxy)
    aweme_id = _extract_aweme_id(url)

    candidate_urls = []
    if aweme_id:
        candidate_urls.append(f"https://www.iesdouyin.com/share/note/{aweme_id}/")
        candidate_urls.append(f"https://www.douyin.com/note/{aweme_id}")
    candidate_urls.append(url)

    detail = None
    last_error = ""
    for target in candidate_urls:
        try:
            resp = session.get(target, timeout=20, allow_redirects=True)
            if resp.status_code == 200 and resp.text:
                detail = _extract_json_from_html(resp.text)
                if detail:
                    break
        except Exception as exc:
            last_error = str(exc)

    if not detail:
        raise RuntimeError(f"未能从抖音图文页面提取作品数据 {last_error}".strip())

    title = (detail.get("desc") or "抖音图文作品").strip()
    author_info = detail.get("author") or {}
    author = (author_info.get("nickname") or author_info.get("unique_id") or "").strip()

    items = []
    images = detail.get("images") or []
    for idx, img in enumerate(images):
        img_url = _pick_best_image_url(img)
        if not img_url:
            continue
        w = img.get("width")
        h = img.get("height")
        quality = f"{w}x{h}" if w and h else f"高清原图 #{idx + 1}"
        items.append({
            "url": img_url,
            "type": "IMAGE",
            "thumbnail": img_url,
            "quality": quality,
            "format_id": f"img_{idx}",
            "ext": "jpg",
        })

    # Extract background music if present
    music = detail.get("music") or {}
    play_url = music.get("play_url") or {}
    music_urls = play_url.get("url_list") or ([play_url.get("uri")] if play_url.get("uri", "").startswith("http") else [])
    if music_urls:
        music_title = (music.get("title") or "背景音乐").strip()
        items.append({
            "url": music_urls[0],
            "type": "AUDIO",
            "thumbnail": items[0]["thumbnail"] if items else "",
            "quality": f"背景音: {music_title}",
            "format_id": "bgm_audio",
            "ext": "mp3",
        })

    if not items:
        raise RuntimeError("该抖音链接未包含可下载的图文或音频资源。")

    has_audio = any(i["type"] == "AUDIO" for i in items)
    has_image = any(i["type"] == "IMAGE" for i in items)
    media_type = "MIXED" if (has_audio and has_image) else "GALLERY"
    return title, author, media_type, items, session


def parse_note(url, cookie_file="", proxy=""):
    try:
        title, author, media_type, items, _ = _extract_note_data(
            url=url,
            cookie_file=cookie_file,
            proxy=proxy,
        )
        cover = items[0]["thumbnail"] if items else ""
        return {
            "status": "ok",
            "platform": "DOUYIN",
            "title": title,
            "author": author,
            "media_type": media_type,
            "thumbnail": cover,
            "items": items,
            "error_message": "",
        }
    except Exception as exc:
        return {
            "status": "error",
            "platform": "DOUYIN",
            "title": "",
            "author": "",
            "media_type": "GALLERY",
            "items": [],
            "error_message": f"{exc}\n{traceback.format_exc()}",
        }


def download_note(
    url,
    output_dir,
    cookie_file="",
    proxy="",
    selected_indices=None,
    progress_callback=None,
):
    os.makedirs(output_dir, exist_ok=True)
    try:
        title, author, media_type, items, session = _extract_note_data(
            url=url,
            cookie_file=cookie_file,
            proxy=proxy,
        )

        if selected_indices:
            target_items = [items[i] for i in selected_indices if 0 <= i < len(items)]
            if not target_items:
                target_items = items
        else:
            target_items = items

        total = len(target_items)
        for idx, item in enumerate(target_items):
            resp = session.get(item["url"], stream=True, timeout=30)
            resp.raise_for_status()

            content_type = (resp.headers.get("Content-Type") or "").lower()
            if item["type"] == "AUDIO":
                ext = "m4a" if "mp4" in content_type or "m4a" in content_type else "mp3"
            else:
                if "png" in content_type:
                    ext = "png"
                elif "webp" in content_type:
                    ext = "webp"
                else:
                    ext = "jpg"

            dest_path = os.path.join(output_dir, f"douyin_note_{idx + 1:03d}.{ext}")
            content_len = int(resp.headers.get("Content-Length") or 0)
            written = 0
            with open(dest_path, "wb") as fp:
                for chunk in resp.iter_content(chunk_size=65536):
                    if not chunk:
                        continue
                    fp.write(chunk)
                    written += len(chunk)
                    if content_len > 0:
                        frac = min(1.0, written / content_len)
                        _emit_progress(progress_callback, 10 + int(((idx + frac) / total) * 85))

            _emit_progress(progress_callback, 10 + int(((idx + 1) / total) * 85))

        return {
            "status": "ok",
            "platform": "DOUYIN",
            "title": title,
            "author": author,
            "media_type": media_type,
            "items": [],
            "error_message": "",
        }
    except Exception as exc:
        return {
            "status": "error",
            "platform": "DOUYIN",
            "title": "",
            "author": "",
            "media_type": "GALLERY",
            "items": [],
            "error_message": f"{exc}\n{traceback.format_exc()}",
        }
