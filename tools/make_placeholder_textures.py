"""Generate placeholder 16x16 block textures for the Noclip mod (no PIL needed)."""
import struct, zlib, os

OUT = r"C:\Users\epete\OneDrive\Documents\Noclip\src\main\resources\assets\noclip\textures\block"

def write_png(path, pixels):
    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("BBB", *p) for p in row)
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", 16, 16, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

def h(x, y, salt=0):
    v = (x * 374761393 + y * 668265263 + salt * 69069) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return (v ^ (v >> 16)) & 0xFF

def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)

def jitter(base, x, y, salt, amt):
    d = (h(x, y, salt) % (2 * amt + 1)) - amt
    return clamp((base[0] + d, base[1] + d, base[2] + d))

def wallpaper():
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            # two-tone vertical stripes, 4px period; no bands so blocks tile cleanly
            base = (203, 186, 112) if (x // 2) % 2 == 0 else (192, 174, 100)
            row.append(jitter(base, x, y, 1, 4))
        px.append(row)
    return px

def ceiling():
    # warm beige tiles, mostly flat with the occasional faint stain
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            if x == 0 or y == 0:
                base = (174, 165, 138)  # tile seam
            elif h(x, y, 2) < 12:
                base = (196, 186, 154)  # water stain
            else:
                base = (211, 202, 172)
            row.append(jitter(base, x, y, 3, 2))
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

os.makedirs(OUT, exist_ok=True)
write_png(os.path.join(OUT, "yellow_wallpaper.png"), wallpaper())
write_png(os.path.join(OUT, "stained_ceiling.png"), ceiling())
write_png(os.path.join(OUT, "damp_carpet.png"), carpet())
write_png(os.path.join(OUT, "fluorescent_light.png"), light())
print("wrote 4 textures to", OUT)
