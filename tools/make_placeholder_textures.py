"""Generate placeholder 16x16 block textures for the Noclip mod (no PIL needed)."""
import struct, zlib, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "noclip", "textures", "block")

def write_png(path, pixels):
    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("BBB", *p) for p in row)
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", 16, 16, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

def read_png(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")
    pos = 8
    width = height = color_type = None
    raw = b""
    while pos < len(data):
        size = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + size]
        pos += 12 + size
        if tag == b"IHDR":
            width, height, bit_depth, color_type = struct.unpack(">IIBB", chunk[:10])[:4]
            if bit_depth != 8 or color_type not in (2, 6):
                raise ValueError(f"{path} must be 8-bit RGB or RGBA")
        elif tag == b"IDAT":
            raw += chunk
    channels = 3 if color_type == 2 else 4
    stride = width * channels
    inflated = zlib.decompress(raw)
    rows = []
    prev = [0] * stride
    i = 0
    for _ in range(height):
        filt = inflated[i]
        i += 1
        scan = list(inflated[i:i + stride])
        i += stride
        row = [0] * stride
        for x, value in enumerate(scan):
            left = row[x - channels] if x >= channels else 0
            up = prev[x]
            upper_left = prev[x - channels] if x >= channels else 0
            if filt == 0:
                predictor = 0
            elif filt == 1:
                predictor = left
            elif filt == 2:
                predictor = up
            elif filt == 3:
                predictor = (left + up) // 2
            else:
                p = left + up - upper_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upper_left)
                predictor = left if pa <= pb and pa <= pc else up if pb <= pc else upper_left
            row[x] = (value + predictor) & 255
        prev = row
        if channels == 3:
            rows.append([tuple(row[x:x + 3]) for x in range(0, stride, channels)])
        else:
            rows.append([tuple(row[x:x + 3]) for x in range(0, stride, channels)])
    return rows

def h(x, y, salt=0):
    v = (x * 374761393 + y * 668265263 + salt * 69069) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return (v ^ (v >> 16)) & 0xFF

def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)

def jitter(base, x, y, salt, amt):
    d = (h(x, y, salt) % (2 * amt + 1)) - amt
    return clamp((base[0] + d, base[1] + d, base[2] + d))

def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def remap_wallpaper_source(path):
    source = read_png(path)
    lumas = [0.299 * r + 0.587 * g + 0.114 * b for row in source for r, g, b in row]
    low, high = min(lumas), max(lumas)
    dark = (191, 176, 105)
    mid = (200, 185, 113)
    light = (207, 192, 120)
    px = []
    for row in source:
        out = []
        for r, g, b in row:
            lum = (0.299 * r + 0.587 * g + 0.114 * b - low) / (high - low or 1)
            base = lerp(dark, mid, lum / 0.55) if lum < 0.55 else lerp(mid, light, (lum - 0.55) / 0.45)
            dirt = round(((r + g + b) / 3 - 153) * 0.08)
            out.append(clamp((base[0] + dirt, base[1] + dirt, base[2] + dirt)))
        px.append(out)
    return px

def wallpaper():
    source = os.path.join(OUT, "yellow_wallpaper_source_green.png")
    if os.path.exists(source):
        # rotate 180 so the chevrons point upward
        px = remap_wallpaper_source(source)
        return [list(reversed(row)) for row in reversed(px)]

    px = []
    # Zigzag path for the flame-stitch motif: which field column (1..3) the
    # dark dot sits in for each row of an 8-row period.
    zig = (2, 1, 1, 2, 3, 3, 2, 2)
    for y in range(16):
        row = []
        for x in range(16):
            # The canonical Backrooms wallpaper: a darker seam column every
            # 4px, and a dotted zigzag serpentine down each field column,
            # drawn at barely more than the noise amplitude. Up close the
            # chevrons read; at a distance the wall flattens to plain yellow.
            mx = x % 4
            if mx == 0:
                base = (194, 180, 108)
            else:
                base = (209, 195, 124) if h(x, y, 10) < 96 else (203, 189, 119)
                yy = (y + (4 if (x // 4) % 2 else 0)) % 8
                if mx == zig[yy]:
                    # dashed, not solid: a full-strength dot on even rows and a
                    # faint one on odd rows keeps the stripes dominant
                    base = (188, 174, 103) if yy % 2 == 0 else (198, 184, 113)
            row.append(jitter(base, x, y, 1, 3 if mx else 2))
        px.append(row)
    return px

def ceiling():
    # yellowed drop-ceiling panels: full soft border, quiet face variation, and
    # very low-contrast speckles so large ceiling fields don't look noisy.
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                base = (181, 174, 140)  # yellowed metal/support seam
            else:
                base = (214, 206, 168)
                roll = h(x, y, 2)
                if roll < 5:
                    base = (204, 195, 157)  # faint stain fleck
                elif roll < 18:
                    base = (209, 201, 163)  # acoustic speckle
                elif roll > 250:
                    base = (219, 211, 174)  # worn high spot
            row.append(jitter(base, x, y, 3, 1))
        px.append(row)
    return px

def carpet():
    # near-flat carpet with a whisper of mottle, like the reference
    shades = [(168, 147, 90), (165, 144, 87), (171, 150, 93)]
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            base = shades[h(x, y, 4) % 3]
            if h(x, y, 5) < 6:
                base = (152, 132, 78)  # damp patch
            row.append(jitter(base, x, y, 6, 2))
        px.append(row)
    return px

def light():
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                base = (128, 128, 120)  # metal frame
            elif x in (5, 10):
                base = (255, 255, 250)  # tube highlights
            else:
                base = (244, 244, 230)
            row.append(jitter(base, x, y, 7, 2))
        px.append(row)
    return px

def dead_light():
    # same panel, unlit: gray tubes, grimy diffuser
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                base = (105, 105, 98)
            elif x in (5, 10):
                base = (172, 172, 162)
            elif h(x, y, 8) < 24:
                base = (128, 126, 116)  # grime
            else:
                base = (156, 155, 146)
            row.append(jitter(base, x, y, 9, 3))
        px.append(row)
    return px

os.makedirs(OUT, exist_ok=True)
# Only textures this script still faithfully reproduces are written.
# Hand-owned, never overwrite: yellow_wallpaper.png, damp_carpet.png,
# fluorescent_light_off.png. Their recipes above are kept for reference.
write_png(os.path.join(OUT, "stained_ceiling.png"), ceiling())
write_png(os.path.join(OUT, "fluorescent_light.png"), light())
print("wrote 2 textures to", OUT)
