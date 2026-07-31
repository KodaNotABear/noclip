# Noclip

A backrooms world type for NeoForge 1.21.1, built as a survival challenge:
complete Minecraft from inside the endless yellow rooms.

## Design pillars

- **World preset instead of a dimension.** The "The Backrooms" world type replaces
  overworld generation. Nether and End keep their vanilla dimension IDs but will be
  rethemed as deeper backrooms levels in later milestones, so portals, the dragon
  fight, and dimension-keyed mod behavior keep working.
- **Regular-world access for beta.** The Backrooms also exist as
  `noclip:backrooms`; craft and use a Noclip Key from a normal world to enter
  Level 0, then use it again inside Level 0 to return to overworld spawn.
- **Unbreakable structure, breakable contents.** Walls, floors, and ceilings are
  unbreakable; props and fixtures are the resource base. The generated
  architecture is a restriction.
- **Mod for the engine, data for the content.** The chunk generator and mechanics
  are Java; rooms, palettes, loot, and compat patches are JSON/NBT. Compat patches
  are plain datapacks gated on `neoforge:conditions` / `mod_loaded`.

## Current state (scaffold)

- Grid-hash Level 0 generator: 8x8 cells, seeded per-segment walls and doorways,
  carpet floor, ceiling tiles with fluorescent panels, sealed above.
- Unbreakable block set + breakable fluorescent light.
- World preset registered under the Normal tab of the world-type cycle button.
- Safe spawn handling (cell centers are always open).
- Hostile and passive spawn lists with vanilla light rules; generated carpet is
  not spawnable, so mob drops come from deliberately authored danger rooms or
  player-built dark rooms instead of every dark atrium floor.
- Room-template system: single-cell rooms authored as structure NBT + JSON
  registry entries, loaded from any datapack namespace, with working block
  entities (loot chests). See [docs/ROOMS.md](docs/ROOMS.md). Ships one
  placeholder supply room; the rest of the room pool is ready for authored
  rooms.
- Noclip Key: crafted from one ender pearl and four paper, for beta access from
  ordinary survival worlds while Level 0 progression is still incomplete.

Planned next: multi-cell rooms + rotation, `neoforge:conditions` on room JSON,
wall veins, salvage tool, config for breakable walls.

## Build & run

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client
```

In the create-world screen, pick world type **The Backrooms**.

For a progression-friendly beta world, create a normal world instead. Craft a
Noclip Key with paper around an ender pearl, then use it to enter or leave
Level 0.

See [docs/PROGRESSION.md](docs/PROGRESSION.md) for the vanilla-item source audit.
