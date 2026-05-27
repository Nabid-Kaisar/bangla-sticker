#!/usr/bin/env python3
"""Generate Bangla sticker WebP images, tray icon, app launcher icons, and contents.json.

Requirements: Pillow built with raqm (for correct Bangla shaping) and HindSiliguri-Bold.ttf.
Run from the repo root:  python3 tools/generate_stickers.py
"""
import json
import math
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


def make_bubble(color):
    """Return (bubble_image, bubble_w, bubble_h) styled like the pack's stickers."""
    bw = CANVAS - 2 * PADDING
    bh = CANVAS - 2 * PADDING
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
    draw.rounded_rectangle([6, 6, bw - 7, bh - 7], radius=BUBBLE_RADIUS - 6,
                           outline=(255, 255, 255, 235), width=8)
    return bubble, bw, bh


def draw_sticker(text, color):
    img = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    bubble, bw, bh = make_bubble(color)
    draw = ImageDraw.Draw(bubble)

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

    img.alpha_composite(bubble, (PADDING, PADDING))
    return img


def draw_cat_face(d, cx, cy, s=1.0):
    """Draw a cartoon cat face centered at (cx, cy), scaled by s."""
    fur = (245, 166, 66)
    fur_dark = (214, 138, 38)
    pink = (240, 150, 160)
    lw = max(2, int(4 * s))

    def p(x, y):
        return (cx + x * s, cy + y * s)

    d.polygon([p(-118, -40), p(-36, -78), p(-96, -158)], fill=fur, outline=fur_dark)
    d.polygon([p(118, -40), p(36, -78), p(96, -158)], fill=fur, outline=fur_dark)
    d.polygon([p(-96, -60), p(-60, -76), p(-92, -132)], fill=pink)
    d.polygon([p(96, -60), p(60, -76), p(92, -132)], fill=pink)
    d.ellipse([p(-125, -109), p(125, 109)], fill=fur, outline=fur_dark, width=lw)
    d.ellipse([p(-108, 28), p(-64, 56)], fill=(255, 180, 170, 160))
    d.ellipse([p(64, 28), p(108, 56)], fill=(255, 180, 170, 160))
    for ex in (-56, 56):
        d.ellipse([p(ex - 34, -48), p(ex + 34, 18)], fill=(255, 255, 255))
        d.ellipse([p(ex - 22, -38), p(ex + 22, 12)], fill=(70, 130, 60))
        d.ellipse([p(ex - 11, -28), p(ex + 11, 8)], fill=(20, 20, 20))
        d.ellipse([p(ex - 4, -24), p(ex + 10, -10)], fill=(255, 255, 255))
    d.polygon([p(-16, 26), p(16, 26), p(0, 44)], fill=pink)
    d.arc([p(-30, 38), p(0, 66)], start=0, end=160, fill=fur_dark, width=lw)
    d.arc([p(0, 38), p(30, 66)], start=20, end=180, fill=fur_dark, width=lw)
    for dy in (-12, 6, 24):
        d.line([p(-40, 30 + dy), p(-150, 18 + dy)], fill=(80, 80, 80), width=lw)
        d.line([p(40, 30 + dy), p(150, 18 + dy)], fill=(80, 80, 80), width=lw)


def draw_cow_face(d, cx, cy, s=1.0):
    """Draw a cartoon cow face centered at (cx, cy), scaled by s."""
    body = (250, 250, 248)
    spot = (74, 74, 78)
    pink = (244, 178, 184)
    pink_dark = (208, 138, 148)
    horn = (228, 212, 170)
    horn_dark = (198, 180, 138)
    lw = max(2, int(4 * s))

    def p(x, y):
        return (cx + x * s, cy + y * s)

    # horns + ears behind head
    d.polygon([p(-78, -86), p(-44, -64), p(-58, -150)], fill=horn, outline=horn_dark)
    d.polygon([p(78, -86), p(44, -64), p(58, -150)], fill=horn, outline=horn_dark)
    d.ellipse([p(-156, -34), p(-92, 22)], fill=body, outline=spot, width=lw)
    d.ellipse([p(92, -34), p(156, 22)], fill=body, outline=spot, width=lw)
    d.ellipse([p(-142, -22), p(-104, 14)], fill=pink)
    d.ellipse([p(104, -22), p(142, 14)], fill=pink)
    # head
    d.ellipse([p(-128, -108), p(128, 116)], fill=body, outline=spot, width=lw)
    # spots
    d.ellipse([p(22, -96), p(104, -26)], fill=spot)
    d.ellipse([p(-112, 26), p(-54, 78)], fill=spot)
    # eyes
    for ex in (-56, 56):
        d.ellipse([p(ex - 22, -54), p(ex + 22, -6)], fill=(28, 28, 30))
        d.ellipse([p(ex - 4, -48), p(ex + 12, -32)], fill=(255, 255, 255))
    # muzzle
    d.ellipse([p(-98, 34), p(98, 122)], fill=pink, outline=pink_dark, width=lw)
    d.ellipse([p(-58, 60), p(-20, 94)], fill=pink_dark)
    d.ellipse([p(20, 60), p(58, 94)], fill=pink_dark)


def draw_cat(bg_color=(144, 202, 249)):
    """Draw a cute cartoon cat face on a styled bubble."""
    img = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    bubble, bw, bh = make_bubble(bg_color)
    d = ImageDraw.Draw(bubble)
    draw_cat_face(d, bw // 2, bh // 2 + 18, 1.0)
    img.alpha_composite(bubble, (PADDING, PADDING))
    return img


def draw_star(d, cx, cy, r, fill, points=5):
    pts = []
    for i in range(points * 2):
        ang = math.pi / points * i - math.pi / 2
        rad = r if i % 2 == 0 else r * 0.45
        pts.append((cx + math.cos(ang) * rad, cy + math.sin(ang) * rad))
    d.polygon(pts, fill=fill)


def draw_eid():
    """Draw a flashy Eid Mubarak sticker with a cow and a cat."""
    img = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    bw = bh = CANVAS - 2 * PADDING

    # festive vertical gradient (deep purple -> magenta)
    top, bot = (74, 20, 140), (200, 24, 96)
    grad = Image.new("RGB", (1, bh))
    for y in range(bh):
        t = y / max(1, bh - 1)
        grad.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    grad = grad.resize((bw, bh))
    mask = rounded_mask((bw, bh), BUBBLE_RADIUS)
    bubble = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    bubble.paste(grad, (0, 0), mask)

    # radiating gold rays (clipped to the bubble)
    rays = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rays)
    rcx, rcy = bw // 2, int(bh * 0.40)
    for i in range(16):
        a = math.radians(i * 22.5)
        rd.polygon([
            (rcx, rcy),
            (rcx + math.cos(a + 0.12) * 1000, rcy + math.sin(a + 0.12) * 1000),
            (rcx + math.cos(a - 0.12) * 1000, rcy + math.sin(a - 0.12) * 1000),
        ], fill=(255, 215, 0, 42))
    clipped = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    clipped.paste(rays, (0, 0), mask)
    bubble.alpha_composite(clipped)

    d = ImageDraw.Draw(bubble)
    d.rounded_rectangle([6, 6, bw - 7, bh - 7], radius=BUBBLE_RADIUS - 6,
                        outline=(255, 255, 255, 235), width=8)

    # crescent moon (carved on its own layer)
    moon = Image.new("RGBA", (120, 120), (0, 0, 0, 0))
    md = ImageDraw.Draw(moon)
    md.ellipse([8, 8, 112, 112], fill=(255, 221, 92, 255))
    md.ellipse([40, 0, 132, 92], fill=(0, 0, 0, 0))
    bubble.alpha_composite(moon, (bw - 150, 26))

    # sparkles + stars
    for sx, sy, r in [(70, 70, 26), (130, 150, 16), (bw - 60, 170, 14), (60, 220, 12)]:
        draw_star(d, sx, sy, r, (255, 236, 140, 255))
    for sx, sy in [(40, 150), (bw - 110, 90), (bw - 40, 250), (150, 60)]:
        d.ellipse([sx - 4, sy - 4, sx + 4, sy + 4], fill=(255, 255, 255, 220))

    # "ঈদ মুবারক" text, gold with dark outline, in the upper band
    font, lines = fit_lines(d, "ঈদ মুবারক", bw - 80, int(bh * 0.26), start=120, min_size=56)
    line_gap = int(font.size * 0.16)
    metrics = [(ln, d.textbbox((0, 0), ln, font=font)) for ln in lines]
    total_h = sum(b[3] - b[1] for _, b in metrics) + line_gap * (len(metrics) - 1)
    y = int(bh * 0.12)
    for ln, b in metrics:
        x = (bw - (b[2] - b[0])) // 2 - b[0]
        d.text((x, y - b[1]), ln, font=font, fill=(255, 215, 0),
               stroke_width=6, stroke_fill=(60, 16, 10))
        y += (b[3] - b[1]) + line_gap

    # cow and cat buddies along the bottom
    draw_cow_face(d, int(bw * 0.31), int(bh * 0.73), 0.56)
    draw_cat_face(d, int(bw * 0.71), int(bh * 0.73), 0.56)

    img.alpha_composite(bubble, (PADDING, PADDING))
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

    # cat illustration sticker
    cat_img = draw_cat()
    save_webp(cat_img, os.path.join(PACK_DIR, "13_biral.webp"), 100 * 1024, lossless=True)
    print(f"  {'13_biral.webp':24s} {os.path.getsize(os.path.join(PACK_DIR, '13_biral.webp')) / 1024:6.1f} KB  cat")
    stickers_meta.append({
        "image_file": "13_biral.webp",
        "emojis": ["\U0001F431"],
        "accessibility_text": "Cat",
    })

    # extra text sticker
    dosh_img = draw_sticker("মিথ্যা দোষ", (211, 47, 47))
    save_webp(dosh_img, os.path.join(PACK_DIR, "14_mittha_dosh.webp"), 100 * 1024, lossless=True)
    print(f"  {'14_mittha_dosh.webp':24s} {os.path.getsize(os.path.join(PACK_DIR, '14_mittha_dosh.webp')) / 1024:6.1f} KB  মিথ্যা দোষ")
    stickers_meta.append({
        "image_file": "14_mittha_dosh.webp",
        "emojis": ["\U0001F644"],
        "accessibility_text": "False blame",
    })

    # flashy Eid Mubarak sticker with a cow and a cat
    eid_img = draw_eid()
    save_webp(eid_img, os.path.join(PACK_DIR, "15_eid_mubarak.webp"), 100 * 1024, lossless=True)
    print(f"  {'15_eid_mubarak.webp':24s} {os.path.getsize(os.path.join(PACK_DIR, '15_eid_mubarak.webp')) / 1024:6.1f} KB  ঈদ মুবারক")
    stickers_meta.append({
        "image_file": "15_eid_mubarak.webp",
        "emojis": ["\U0001F319", "✨"],
        "accessibility_text": "Eid Mubarak with a cow and a cat",
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
            "image_data_version": "3",
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
