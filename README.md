# Noclip

A backrooms world type for NeoForge 1.21.1, built as a survival challenge:
complete Minecraft from inside the endless yellow rooms.

## Design pillars

- **World preset instead of a dimension.** The "The Backrooms" world type replaces
  overworld generation. Nether and End keep their vanilla dimension IDs but will be
  rethemed as deeper backrooms levels in later milestones, so portals, the dragon
  fight, and dimension-keyed mod behavior keep working.
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
- Hostile and passive spawn lists with vanilla light rules; dead fluorescent
  panels and blackout pockets provide the spawnable darkness.
- Room-template system: single-cell rooms authored as structure NBT + JSON
  registry entries, loaded from any datapack namespace, with working block
  entities (loot chests). See [docs/ROOMS.md](docs/ROOMS.md). Ships two
  placeholder rooms (pillar hall, supply room); the 2x2 big-room pool is
  empty until authored rooms land.

Planned next: multi-cell rooms + rotation, `neoforge:conditions` on room JSON,
wall veins, salvage tool, config for breakable walls.

## Build & run

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client
```

In the create-world screen, pick world type **The Backrooms**.

See [docs/PROGRESSION.md](docs/PROGRESSION.md) for the vanilla-item source audit.
