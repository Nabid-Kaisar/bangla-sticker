#!/usr/bin/env python3
"""Generate Bangla sticker WebP images, tray icon, app launcher icons, and contents.json.

Requirements: Pillow built with raqm (for correct Bangla shaping) and HindSiliguri-Bold.ttf.
Run from the repo root:  python3 tools/generate_stickers.py
"""
import json
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FONT_PATH = os.path.join(HERE, "HindSiliguri-Bold.ttf")
PACK_ID = "bangla_pack_1"
PACK_DIR = os.path.join(ROOT, "app", "src", "main", "assets", PACK_ID)
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res")

CANVAS = 512
PADDING = 28          # transparent margin around the bubble
BUBBLE_RADIUS = 64
INNER_PAD = 44        # padding inside the bubble before text

RAQM = ImageFont.Layout.RAQM

# (filename, bangla text, emojis, english accessibility text, bubble color)
STICKERS = [
    ("01_bhalobasha.webp", "ভালোবাসা",   ["❤️"], "Love",         (233, 30, 99)),
    ("02_dhonnobad.webp",  "ধন্যবাদ",     ["\U0001F64F"],   "Thank you",    (63, 81, 181)),
    ("03_hello.webp",      "হ্যালো",       ["\U0001F44B"],   "Hello",        (3, 169, 244)),
    ("04_darun.webp",      "দারুণ!",       ["\U0001F44D"],   "Awesome",      (255, 152, 0)),
    ("05_hahaha.webp",     "হা হা হা",     ["\U0001F602"],   "Haha",         (255, 193, 7)),
    ("06_ki_khobor.webp",  "কী খবর?",      ["\U0001F914"],   "What's up",    (0, 150, 136)),
    ("07_thik_ache.webp",  "ঠিক আছে",      ["\U0001F44C"],   "OK",           (76, 175, 80)),
    ("08_shuvo_sokal.webp","শুভ সকাল",     ["\U0001F305"],   "Good morning", (255, 87, 34)),
    ("09_shuvo_ratri.webp","শুভ রাত্রি",   ["\U0001F319"],   "Good night",   (48, 63, 159)),
    ("10_mon_kharap.webp", "মন খারাপ",     ["\U0001F622"],   "Sad",          (96, 125, 139)),
    ("11_obhinondon.webp", "অভিনন্দন!",    ["\U0001F389"],   "Congrats",     (156, 39, 176)),
    ("12_dukkhito.webp",   "দুঃখিত",       ["\U0001F605"],   "Sorry",        (121, 85, 72)),
]


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def lighten(color, amount):
    return tuple(min(255, int(c + (255 - c) * amount)) for c in color)


def fit_lines(draw, text, max_w, max_h, start=190, min_size=64):
    """Return (font, [lines]) sized so the text fits in max_w x max_h.

    Tries single line first; if a word break exists, also tries two lines.
    """
    words = text.split(" ")
    candidates = [[text]]
    if len(words) > 1:
        mid = (len(words) + 1) // 2
        candidates.append([" ".join(words[:mid]), " ".join(words[mid:])])

    best = None
    for lines in candidates:
        size = start
        while size >= min_size:
            font = ImageFont.truetype(FONT_PATH, size, layout_engine=RAQM)
            widths, heights = [], []
            for ln in lines:
                b = draw.textbbox((0, 0), ln, font=font)
                widths.append(b[2] - b[0])
                heights.append(b[3] - b[1])
            line_gap = int(size * 0.18)
            total_h = sum(heights) + line_gap * (len(lines) - 1)
            if max(widths) <= max_w and total_h <= max_h:
                cand = (size, font, lines)
                if best is None or size > best[0]:
                    best = cand
                break
            size -= 4
    if best is None:
        font = ImageFont.truetype(FONT_PATH, min_size, layout_engine=RAQM)
        best = (min_size, font, candidates[-1])
    return best[1], best[2]


def draw_sticker(text, color):
    img = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    bubble_box = (PADDING, PADDING, CANVAS - PADDING, CANVAS - PADDING)
    bw = bubble_box[2] - bubble_box[0]
    bh = bubble_box[3] - bubble_box[1]

    # bubble with vertical gradient + white outline
    bubble = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    grad = Image.new("RGB", (1, bh))
    top = lighten(color, 0.18)
    for y in range(bh):
        t = y / max(1, bh - 1)
        grad.putpixel((0, y), tuple(int(top[i] + (color[i] - top[i]) * t) for i in range(3)))
    grad = grad.resize((bw, bh))
    mask = rounded_mask((bw, bh), BUBBLE_RADIUS)
    bubble.paste(grad, (0, 0), mask)

    draw = ImageDraw.Draw(bubble)
    # inner white outline
    draw.rounded_rectangle([6, 6, bw - 7, bh - 7], radius=BUBBLE_RADIUS - 6,
                           outline=(255, 255, 255, 235), width=8)

    inner_w = bw - 2 * INNER_PAD
    inner_h = bh - 2 * INNER_PAD
    font, lines = fit_lines(draw, text, inner_w, inner_h)

    metrics = []
    line_gap = int(font.size * 0.18)
    for ln in lines:
        b = draw.textbbox((0, 0), ln, font=font)
        metrics.append((ln, b))
    total_h = sum(b[3] - b[1] for _, b in metrics) + line_gap * (len(metrics) - 1)

    y = (bh - total_h) // 2
    for ln, b in metrics:
        w = b[2] - b[0]
        x = (bw - w) // 2 - b[0]
        yy = y - b[1]
        # soft shadow then white text
        draw.text((x + 3, yy + 4), ln, font=font, fill=(0, 0, 0, 90))
        draw.text((x, yy), ln, font=font, fill=(255, 255, 255, 255))
        y += (b[3] - b[1]) + line_gap

    img.alpha_composite(bubble, (bubble_box[0], bubble_box[1]))
    return img


def save_webp(img, path, max_bytes, lossless=True):
    q = 100
    img.save(path, "WEBP", lossless=lossless, quality=q, method=6)
    if os.path.getsize(path) <= max_bytes:
        return
    # fall back to lossy and step quality down until under the limit
    for q in range(90, 9, -10):
        img.save(path, "WEBP", lossless=False, quality=q, method=6)
        if os.path.getsize(path) <= max_bytes:
            return


def make_tray():
    size = 96
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = rounded_mask((size, size), 24)
    grad = Image.new("RGB", (size, size), (233, 30, 99))
    img.paste(grad, (0, 0), mask)
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT_PATH, 52, layout_engine=RAQM)
    txt = "বা"
    b = draw.textbbox((0, 0), txt, font=font)
    w, h = b[2] - b[0], b[3] - b[1]
    draw.text(((size - w) // 2 - b[0], (size - h) // 2 - b[1]), txt,
              font=font, fill=(255, 255, 255, 255))
    save_webp(img, os.path.join(PACK_DIR, "tray.webp"), 50 * 1024, lossless=True)


def make_app_icon():
    base = 432  # adaptive-style foreground on 512 canvas
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    mask = rounded_mask((512, 512), 96)
    bg = Image.new("RGB", (512, 512), (233, 30, 99))
    canvas.paste(bg, (0, 0), mask)
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(FONT_PATH, 220, layout_engine=RAQM)
    txt = "বাং"
    font = ImageFont.truetype(FONT_PATH, 200, layout_engine=RAQM)
    b = draw.textbbox((0, 0), txt, font=font)
    w, h = b[2] - b[0], b[3] - b[1]
    draw.text(((512 - w) // 2 - b[0], (512 - h) // 2 - b[1]), txt,
              font=font, fill=(255, 255, 255, 255))
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, px in densities.items():
        out = os.path.join(RES_DIR, f"mipmap-{d}")
        os.makedirs(out, exist_ok=True)
        canvas.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))
        canvas.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher_round.png"))


def main():
    os.makedirs(PACK_DIR, exist_ok=True)
    stickers_meta = []
    for fname, text, emojis, alt, color in STICKERS:
        img = draw_sticker(text, color)
        save_webp(img, os.path.join(PACK_DIR, fname), 100 * 1024, lossless=True)
        size_kb = os.path.getsize(os.path.join(PACK_DIR, fname)) / 1024
        print(f"  {fname:24s} {size_kb:6.1f} KB  {text}")
        stickers_meta.append({
            "image_file": fname,
            "emojis": emojis,
            "accessibility_text": alt,
        })

    make_tray()
    make_app_icon()

    contents = {
        "android_play_store_link": "",
        "ios_app_store_link": "",
        "sticker_packs": [{
            "identifier": PACK_ID,
            "name": "Bangla Stickers",
            "publisher": "Bangla Sticker App",
            "tray_image_file": "tray.webp",
            "image_data_version": "1",
            "avoid_cache": False,
            "publisher_email": "",
            "publisher_website": "",
            "privacy_policy_website": "",
            "license_agreement_website": "",
            "animated_sticker_pack": False,
            "stickers": stickers_meta,
        }],
    }
    with open(os.path.join(ROOT, "app", "src", "main", "assets", "contents.json"), "w",
              encoding="utf-8") as f:
        json.dump(contents, f, ensure_ascii=False, indent=2)
    print("Wrote contents.json with", len(stickers_meta), "stickers")


if __name__ == "__main__":
    main()
