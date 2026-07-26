"""Generate placeholder single-cell room templates as vanilla structure NBT.

Rooms are 7x4x7 (a cell interior). Real rooms should be authored in-game with
structure blocks and saved over these. Run from the repo root:
    python tools/make_placeholder_rooms.py
"""
import struct, gzip, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "data", "noclip", "structure", "rooms")
DATA_VERSION = 3955  # 1.21.1


def tag_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b

def named(tagtype, name, payload):
    return bytes([tagtype]) + tag_string(name) + payload

def t_int(v):
    return struct.pack(">i", v)

def t_compound(items):  # items: list of (tagtype, name, payload); adds TAG_End
    return b"".join(named(t, n, p) for t, n, p in items) + b"\x00"

def t_list(tagtype, payloads):
    return bytes([tagtype]) + struct.pack(">i", len(payloads)) + b"".join(payloads)

def palette_entry(name, props=None):
    items = [(8, "Name", tag_string(name))]
    if props:
        items.append((10, "Properties", t_compound([(8, k, tag_string(v)) for k, v in props.items()])))
    return t_compound(items)

def block(pos, state, nbt_items=None):
    items = [(9, "pos", t_list(3, [t_int(p) for p in pos])), (3, "state", t_int(state))]
    if nbt_items:
        items.append((10, "nbt", t_compound(nbt_items)))
    return t_compound(items)

def write_structure(filename, palette, blocks, size=(7, 4, 7)):
    root = t_compound([
        (9, "size", t_list(3, [t_int(v) for v in size])),
        (9, "entities", t_list(0, [])),
        (9, "blocks", t_list(10, blocks)),
        (9, "palette", t_list(10, palette)),
        (3, "DataVersion", t_int(DATA_VERSION)),
    ])
    os.makedirs(OUT, exist_ok=True)
    with gzip.open(os.path.join(OUT, filename), "wb") as f:
        f.write(named(10, "", root))


def loot_nbt(block_id, table):
    return [(8, "id", tag_string(block_id)), (8, "LootTable", tag_string(table))]


# --- pillar_room: four structural pillars ---------------------------------
WALLPAPER = palette_entry("noclip:yellow_wallpaper")
blocks = [block([x, y, z], 0) for x, z in [(1, 1), (1, 5), (5, 1), (5, 5)] for y in range(4)]
write_structure("pillar_room.nbt", [WALLPAPER], blocks)

# --- atrium: 2x2-cell, 12 tall, light-banded pillars + loot cluster -------
palette = [
    palette_entry("noclip:yellow_wallpaper"),
    palette_entry("noclip:fluorescent_light"),
    palette_entry("minecraft:chest", {"facing": "south"}),
    palette_entry("minecraft:barrel", {"facing": "up"}),
]
blocks = []
for px, pz in [(2, 2), (2, 11), (11, 2), (11, 11)]:
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(12):
                state = 1 if y in (5, 10) else 0  # light bands
                blocks.append(block([px + dx, y, pz + dz], state))
blocks.append(block([7, 0, 8], 2, loot_nbt("minecraft:chest", "noclip:chests/level0_supply")))
blocks.append(block([6, 0, 7], 3, loot_nbt("minecraft:barrel", "noclip:chests/level0_supply")))
blocks.append(block([8, 0, 7], 3, loot_nbt("minecraft:barrel", "noclip:chests/level0_supply")))
write_structure("atrium.nbt", palette, blocks, size=(15, 12, 15))

# --- supply_room: lootable chest flanked by barrels -----------------------
palette = [
    palette_entry("minecraft:chest", {"facing": "south"}),
    palette_entry("minecraft:barrel", {"facing": "up"}),
]
blocks = [
    block([3, 0, 3], 0, loot_nbt("minecraft:chest", "noclip:chests/level0_supply")),
    block([2, 0, 3], 1, loot_nbt("minecraft:barrel", "noclip:chests/level0_supply")),
    block([4, 0, 3], 1, loot_nbt("minecraft:barrel", "noclip:chests/level0_supply")),
]
write_structure("supply_room.nbt", palette, blocks)

print("wrote 3 room templates to", os.path.normpath(OUT))
