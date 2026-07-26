# Architecture

How the pieces fit together, file by file. Companion docs: [ROOMS.md](ROOMS.md)
(room authoring), [PROGRESSION.md](PROGRESSION.md) (design audit).

## The one idea everything hangs off

Chunks must be generatable **independently, in any order, on any thread**. So
the world layout is never stored or simulated; every block is a pure function
`stateAt(seed, x, y, z)`. Any question about the world ("is there a wall here?
which room is in this cell?") is answered by hashing the world seed with the
position and a salt (one salt per independent decision, so decisions don't
correlate). Two chunks that both need to know about the same wall segment
compute the same hash and agree without communicating.

## Build system

- `build.gradle` / `settings.gradle` / `gradle.properties`: NeoForge 21.1.219
  on MC 1.21.1 via ModDevGradle. `gradlew build` makes the jar, `gradlew
  runClient` launches a dev client, `gradlew runData` runs datagen (unused so
  far). Versions live in `gradle.properties`; the mods.toml is templated from
  them at build time.

## Java (`src/main/java/studio/akuro/noclip/`)

### `Noclip.java`
Entry point. Registers the four `DeferredRegister`s (blocks, items, creative
tab, chunk generator codec) onto the mod event bus. Nothing else.

### `block/NoclipBlocks.java`
The Level 0 palette. Structural blocks (wallpaper, ceiling tile, carpet) use
`strength(-1, 3600000)` + `noLootTable()` (the bedrock recipe), so the
generated architecture is unbreakable in survival. The fluorescent light is a
normal breakable block with `lightLevel(13)`. Design rule: structure is
unbreakable, contents/props are the resource base.

### `item/NoclipItems.java`, `item/NoclipCreativeTabs.java`
BlockItems for all four blocks and one creative tab holding them.

### `worldgen/NoclipWorldgen.java`
Registers the generator's `MapCodec` into `Registries.CHUNK_GENERATOR`. That
codec is how a world save (or the world preset JSON) names and reconstructs the
generator: `"type": "noclip:backrooms"`.

### `worldgen/BackroomsChunkGenerator.java`: the core
Extends vanilla's abstract `ChunkGenerator`. Vanilla drives it through the
chunk pipeline; the two methods that matter:

- **`fillFromNoise`**: called once per chunk (on worker threads). Loops all
  16x16 columns, asks `stateAt` for every y, writes non-air blocks and primes
  the two worldgen heightmaps. Then `placeRoomBlockEntities` attaches pending
  block-entity NBT (loot chests) for any room cells in this chunk.
- **`getBaseColumn`**: same `stateAt` logic, used by vanilla for previews and
  structure checks, so it agrees with real generation by construction.

The layout, bottom to top: y=0 carpet floor; y=1..4 the playable room space;
y=5 ceiling tiles with a fluorescent panel at each cell center; solid wallpaper
fill above so there is no "on top of the backrooms". The dimension is 64 blocks
tall (see dimension_type JSON); tall spaces (warehouses, big rooms) carve upward
into the fill and provide their own ceilings.

The horizontal layout is an 8-block cell grid. Walls live on grid lines
(`x % 8 == 0` or `z % 8 == 0`); intersections are always pillars. Each cell's
west and north wall segments are decided by `segmentOpening`:

1. **Binary-tree maze**: each cell hash-picks exactly one of {west, north} to
   carve open. This alone guarantees every cell in the infinite grid is
   reachable. No flood fill, no global state; each decision is one hash.
2. **Braiding**: extra hash rolls reopen some of the remaining walls, creating
   loops so the maze isn't a single spindly tree.
3. **Zones**: a coarse hash over 8x8-cell regions (64 blocks) picks the region
   type (tight corridor maze, mixed, open pillar halls, or **warehouse**), so
   the traversal feel varies across the map. Shown on the F3 debug screen.

An opening is either a 3-wide doorway (centered on the cell-center axis, aligned with the ceiling lights) or the full segment (another hash).

**Warehouses** (~8% of zones) are single tall voids: ceiling at y=24, 2x2
mega-pillars every 16 blocks with fluorescent light bands, no maze walls.
Adjacent warehouse zones merge; borders against normal zones get full-height
transition walls with door gaps. Connectivity survives zone borders because a
cell whose binary-tree carve crosses a border gets a doorway instead of a wall,
plus one guaranteed door per border side.

**Big rooms** (2x2 cells, ~11% of even-aligned anchors) stamp 15-wide templates
up to 12 tall; the generator raises their ceiling to the template height. Even
anchoring means a big room is exactly one chunk: no overlap resolution, no
caching, and block entities stay in-chunk. Warehouse zones exclude both room
kinds.

`layoutSeed` derives the layout's 64-bit seed from the world seed via
`RandomState.getOrCreateRandomFactory` (the vanilla-sanctioned way to get
seeded randomness in a custom generator; `RandomState` has no direct seed
accessor).

Everything else (`applyCarvers`, `buildSurface`, `spawnOriginalMobs`) is a
deliberate no-op.

### `worldgen/room/`: the data-driven content layer
- **`RoomDefinition`**: codec for the registry JSON (template id + weight).
- **`RoomGrid`**: decodes a structure NBT into a flat `BlockState[7*4*7]`
  (null = defer to base generator, from `structure_void`) plus a list of
  block-entity NBT tags. Parsing is manual (`NbtIo` + `NbtUtils.readBlockState`)
  rather than vanilla `StructureTemplate` because worldgen threads have no
  `ServerLevel` to hand to `placeInWorld`; we need raw states we can sample per
  block inside `stateAt`.
- **`RoomManager`**: a `SimpleJsonResourceReloadListener` over
  `data/<ns>/backrooms_rooms/`, re-registered on every datapack load via
  `AddReloadListenerEvent` (which supplies the block registry needed to decode
  states). Loads JSON + NBT into a static, immutable, volatile list. Ids are
  sorted so the weighted order is identical across restarts, which is what makes
  seeded selection stable.

Selection (`roomAt` in the generator): per-cell presence hash (~12.5% of
cells), then a weighted pick over the pool. Room templates only cover the cell
interior (7x4x7, local coords 1..7); boundary columns always belong to the
maze. That boundary rule is load-bearing: sampling the grid on a boundary
column is the out-of-bounds crash we already fixed once.

### `worldgen/BackroomsSpawnHandler.java`
Vanilla spawn placement walks heightmaps, which would put players on the sealed
roof. The dimension type sets `has_ceiling: true`, which makes respawn logic ask
the generator (`getSpawnHeight` → y=1), and this handler cancels
`LevelEvent.CreateSpawnPosition` to pin world spawn to a cell center. Cell
centers are guaranteed open because walls only exist on grid lines.

## Resources (`src/main/resources/`)

### `data/noclip/`: worldgen wiring (all datapack-format JSON)
- **`dimension_type/backrooms.json`**: 64 tall, min_y 0, no skylight,
  `has_ceiling` true (spawn logic + thematically true), `ambient_light` 0.3
  (the main brightness knob), nether-style effects (no sky rendering).
- **`worldgen/biome/level_0.json`**: one biome: yellow fog, cave mood sounds,
  no features. Spawn lists carry the full overworld cast (hostiles including
  slime and zombie villager, plus passive animals for player-built pastures).
  Spawnable darkness comes from the generator: ~15% of ceiling panels are dead,
  and 4x4-cell blackout pockets kill every panel; vanilla's block-light-0 rule
  does the rest.
- **`worldgen/world_preset/backrooms.json`**: the "world type". Overworld uses
  `noclip:backrooms` generator + dimension type with a fixed `noclip:level_0`
  biome source; Nether and End are vanilla copies for now (they keep their
  dimension IDs forever; later they get rethemed *generation*, so portals,
  the dragon fight, and dimension-keyed mod logic keep working).
- **`data/minecraft/tags/worldgen/world_preset/normal.json`**: injects the
  preset into the create-world screen's world-type cycle.
- **`backrooms_rooms/*.json` + `structure/rooms/*.nbt`**: the shipped room
  pool (see ROOMS.md).
- **`loot_table/`**: block drops (fluorescent light) and the supply-room chest
  table (`chests/level0_supply`).

### `assets/noclip/`: client-side looks
Blockstates → models → textures, one per block, plus `lang/en_us.json` (block
names, creative tab, world preset name; its key is `generator.noclip.backrooms`).
The carpet blockstate lists four y-rotations of the same model, vanilla's
grass-block trick to break up tiling. Textures are 16x16 placeholder PNGs.

## `tools/`
- `make_placeholder_textures.py`: deterministic pixel-noise placeholder
  textures. Rerun after tweaking palettes in the script.
- `make_placeholder_rooms.py`: writes the placeholder room NBTs with a
  minimal hand-rolled NBT encoder. Real rooms should be authored in-game with
  structure blocks instead (workflow in ROOMS.md).

## Debugging notes
- Dev client logs: `run/client/logs/latest.log`. A world-creation screen stuck
  at 0% usually means an exception inside `fillFromNoise`. The progress UI
  swallows it; the log has the real stack.
- `Loaded N backrooms rooms` in the log on world load is the health check that
  the room pipeline parsed everything.
- Lighting/emission changes don't relight already-generated chunks; judge
  lighting tweaks in fresh chunks or a new world.
