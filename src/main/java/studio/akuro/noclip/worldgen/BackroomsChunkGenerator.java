package studio.akuro.noclip.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.Nullable;
import studio.akuro.noclip.Noclip;
import studio.akuro.noclip.block.FluorescentLightBlock;
import studio.akuro.noclip.block.NoclipBlocks;
import studio.akuro.noclip.worldgen.room.RoomGrid;
import studio.akuro.noclip.worldgen.room.RoomManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Level 0: an endless grid of yellow rooms.
 *
 * Every block is a pure function of (world seed, position): any question about
 * the layout is answered by hashing the seed with the position and a salt, so
 * any chunk can generate independently, in any order, on any thread.
 *
 * Layout layers, decided per column:
 * 1. Zones (8x8 cells = 64 blocks): corridor maze / mixed / open halls /
 *    warehouse. Warehouses are 23-block-tall voids with mega-pillars.
 * 2. Big rooms: 2x2-cell templates anchored on even cell coords (so they align
 *    with chunks and can never overlap each other).
 * 3. The cell maze: binary-tree carve (guaranteed connectivity) + braid loops,
 *    with single-cell room templates stamped into some interiors.
 */
public class BackroomsChunkGenerator extends ChunkGenerator {
    public static final MapCodec<BackroomsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource)
            ).apply(instance, BackroomsChunkGenerator::new));

    /** Grid cell size in blocks; walls sit on multiples of this. */
    private static final int CELL = 8;
    /** Zone size in cells (64 blocks square). */
    private static final int ZONE_CELLS = 8;
    private static final int FLOOR_Y = 0;
    private static final int CEILING_Y = 5;
    private static final int WALL_MIN_Y = 1;
    private static final int WALL_MAX_Y = 4;
    private static final int WAREHOUSE_CEILING_Y = 24;

    // Hash salts, one per independent decision.
    private static final int SALT_ROOM_PRESENCE = 0x53;
    private static final int SALT_ROOM_PICK = 0x54;
    private static final int SALT_CARVE = 0x55;
    private static final int SALT_BRAID = 0x56;
    private static final int SALT_KIND = 0x58;
    private static final int SALT_ZONE = 0x5A;
    private static final int SALT_BIG_PRESENCE = 0x5C;
    private static final int SALT_BIG_PICK = 0x5D;
    private static final int SALT_BORDER = 0x5E;
    private static final int SALT_ROOM_ROT = 0x60;
    private static final int SALT_BIG_ROT = 0x62;
    private static final int SALT_SEALED_DOORS = 0x64;
    private static final int SALT_DEAD_LIGHT = 0x66;
    private static final int SALT_BLACKOUT = 0x68;

    /** Chance (out of 256) that a cell hosts a single-cell room. */
    private static final int ROOM_CHANCE = 32;
    /** Chance (out of 256) that an even-aligned 2x2 cell block hosts a big room. */
    private static final int BIG_ROOM_CHANCE = 28;

    private enum Opening {
        SOLID, DOOR, OPEN
    }

    /** Per-64x64-block region layout parameters. */
    private record Zone(boolean warehouse, int braidChance, int openKindChance) {
    }

    private static final ResourceLocation LAYOUT_RANDOM =
            ResourceLocation.fromNamespaceAndPath(Noclip.MOD_ID, "backrooms_layout");

    /** Stable per-world 64-bit seed for the grid layout, derived from the world seed. */
    private static long layoutSeed(RandomState randomState) {
        return randomState.getOrCreateRandomFactory(LAYOUT_RANDOM).fromHashOf("layout").nextLong();
    }

    public BackroomsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        long seed = layoutSeed(randomState);
        ChunkPos chunkPos = chunk.getPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                for (int y = minY; y < maxY; y++) {
                    BlockState state = stateAt(seed, worldX, y, worldZ);
                    if (!state.isAir()) {
                        chunk.setBlockState(pos.set(localX, y, localZ), state, false);
                        oceanFloor.update(localX, y, localZ, state);
                        worldSurface.update(localX, y, localZ, state);
                    }
                }
            }
        }
        placeRoomBlockEntities(seed, chunkPos, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * The single source of truth for what block occupies a world position.
     * Deterministic in (seed, x, y, z).
     */
    private BlockState stateAt(long seed, int x, int y, int z) {
        if (y == FLOOR_Y) {
            return NoclipBlocks.DAMP_CARPET.get().defaultBlockState();
        }
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);

        if (zoneAt(seed, cellX, cellZ).warehouse()) {
            return warehouseState(seed, x, y, z, cellX, cellZ);
        }

        int anchorX = cellX & ~1;
        int anchorZ = cellZ & ~1;
        RoomManager.LoadedRoom bigRoom = bigRoomAt(seed, anchorX, anchorZ);
        if (bigRoom != null) {
            return bigRoomState(seed, bigRoom, anchorX, anchorZ, x, y, z);
        }

        return normalCellState(seed, x, y, z, cellX, cellZ);
    }

    // --- Normal maze cells -------------------------------------------------

    private BlockState normalCellState(long seed, int x, int y, int z, int cellX, int cellZ) {
        if (y == CEILING_Y) {
            // Wall columns continue as wallpaper through ceiling height, so wall
            // faces exposed to taller volumes never show a tile stripe.
            if (isWallColumn(seed, x, z)) {
                return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
            }
            if (Math.floorMod(x, CELL) == CELL / 2 && Math.floorMod(z, CELL) == CELL / 2) {
                return ceilingLight(seed, cellX, cellZ);
            }
            int localX = Math.floorMod(x, CELL);
            int localZ = Math.floorMod(z, CELL);
            // Door/open lintels facing a tall space read as wall, not ceiling.
            if ((localX == 0 && tallSpaceAt(seed, cellX - 1, cellZ))
                    || (localZ == 0 && tallSpaceAt(seed, cellX, cellZ - 1))) {
                return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
            }
            return NoclipBlocks.STAINED_CEILING.get().defaultBlockState();
        }
        if (y > CEILING_Y) {
            // Solid structural fill up to the world ceiling: nothing to find up there.
            return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        }
        if (y >= WALL_MIN_Y && y <= WALL_MAX_Y) {
            if (isWallColumn(seed, x, z)) {
                return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
            }
            int localX = Math.floorMod(x, CELL);
            int localZ = Math.floorMod(z, CELL);
            // Only strict-interior columns sample the room template; boundary
            // columns (doorways and open segments, localX/Z == 0) stay clear.
            if (localX >= 1 && localZ >= 1) {
                RoomGrid room = roomAt(seed, cellX, cellZ);
                if (room != null) {
                    BlockState state = room.sample(localX - 1, y - WALL_MIN_Y, localZ - 1,
                            rotationAt(seed, cellX, cellZ, SALT_ROOM_ROT));
                    if (state != null) {
                        return state;
                    }
                }
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    // --- Warehouse zones ---------------------------------------------------

    /**
     * A warehouse zone is one tall open volume: floor at 0, ceiling at
     * {@link #WAREHOUSE_CEILING_Y}, 2x2 mega-pillars every 16 blocks with
     * glowing light bands, and full-height transition walls (with door gaps)
     * where the zone borders normal cells.
     */
    private BlockState warehouseState(long seed, int x, int y, int z, int cellX, int cellZ) {
        boolean isolated = isolatedWarehouse(seed, cellX, cellZ);
        if (y == WAREHOUSE_CEILING_Y) {
            // Isolated (single-zone) warehouses have no pillars, so they get
            // ceiling panels for some glow; merged ones are lit by pillar bands.
            if (isolated && Math.floorMod(x, CELL) == CELL / 2 && Math.floorMod(z, CELL) == CELL / 2) {
                return ceilingLight(seed, cellX, cellZ);
            }
            return NoclipBlocks.STAINED_CEILING.get().defaultBlockState();
        }
        if (y > WAREHOUSE_CEILING_Y) {
            return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        }
        // y in 1..23 from here down.
        int localX = Math.floorMod(x, CELL);
        int localZ = Math.floorMod(z, CELL);

        // Transition walls toward non-warehouse neighbors, full height.
        if (localX == 0 && localZ == 0) {
            boolean westNormal = !zoneAt(seed, cellX - 1, cellZ).warehouse();
            boolean northNormal = !zoneAt(seed, cellX, cellZ - 1).warehouse();
            if (westNormal || northNormal) {
                return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
            }
        } else if (localX == 0 && !zoneAt(seed, cellX - 1, cellZ).warehouse()) {
            return transitionWallState(seed, cellX, cellZ, true, localZ, y);
        } else if (localZ == 0 && !zoneAt(seed, cellX, cellZ - 1).warehouse()) {
            return transitionWallState(seed, cellX, cellZ, false, localX, y);
        }

        // Mega-pillars on the 16-block grid, with light bands. Only merged
        // (multi-zone) warehouses get pillars; a pillar only stands if all four
        // cells meeting at its corner are warehouse, so pillars never
        // half-merge into zone transition walls.
        if (!isolated && Math.floorMod(x, 2 * CELL) < 2 && Math.floorMod(z, 2 * CELL) < 2) {
            int pillarCellX = 2 * Math.floorDiv(x, 2 * CELL);
            int pillarCellZ = 2 * Math.floorDiv(z, 2 * CELL);
            if (zoneAt(seed, pillarCellX, pillarCellZ).warehouse()
                    && zoneAt(seed, pillarCellX - 1, pillarCellZ).warehouse()
                    && zoneAt(seed, pillarCellX, pillarCellZ - 1).warehouse()
                    && zoneAt(seed, pillarCellX - 1, pillarCellZ - 1).warehouse()) {
                if (y == 4 || y == 12 || y == 20) {
                    return NoclipBlocks.FLUORESCENT_LIGHT.get().defaultBlockState();
                }
                return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * Cell-center ceiling panel, lit or dead. Dead panels are what create
     * spawnable darkness: ~15% of cells individually, plus 4x4-cell blackout
     * pockets (~5% of them) where every panel is out. Vanilla spawn rules do
     * the rest (monsters need block light 0).
     */
    private BlockState ceilingLight(long seed, int cellX, int cellZ) {
        boolean blackout = (hash(seed, Math.floorDiv(cellX, 4), Math.floorDiv(cellZ, 4), SALT_BLACKOUT) & 0xFF) < 20;
        boolean dead = blackout || (hash(seed, cellX, cellZ, SALT_DEAD_LIGHT) & 0xFF) < 38;
        return NoclipBlocks.FLUORESCENT_LIGHT.get().defaultBlockState()
                .setValue(FluorescentLightBlock.LIT, !dead);
    }

    /** A warehouse zone with no orthogonally adjacent warehouse zone. */
    private boolean isolatedWarehouse(long seed, int cellX, int cellZ) {
        int zoneMinCellX = Math.floorDiv(cellX, ZONE_CELLS) * ZONE_CELLS;
        int zoneMinCellZ = Math.floorDiv(cellZ, ZONE_CELLS) * ZONE_CELLS;
        return !zoneAt(seed, zoneMinCellX - 1, zoneMinCellZ).warehouse()
                && !zoneAt(seed, zoneMinCellX + ZONE_CELLS, zoneMinCellZ).warehouse()
                && !zoneAt(seed, zoneMinCellX, zoneMinCellZ - 1).warehouse()
                && !zoneAt(seed, zoneMinCellX, zoneMinCellZ + ZONE_CELLS).warehouse();
    }

    private BlockState transitionWallState(long seed, int cellX, int cellZ, boolean west, int along, int y) {
        Opening opening = segmentOpening(seed, cellX, cellZ, west);
        if (opening == Opening.DOOR && y <= WALL_MAX_Y && isDoorGap(along)) {
            return Blocks.AIR.defaultBlockState();
        }
        return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
    }

    /** 3-wide gap centered on the cell-center axis, aligned with the ceiling lights. */
    private static boolean isDoorGap(int along) {
        return along >= 3 && along <= 5;
    }

    /** True if this cell is a tall volume: warehouse zone or part of a big room. */
    private boolean tallSpaceAt(long seed, int cellX, int cellZ) {
        return zoneAt(seed, cellX, cellZ).warehouse()
                || bigRoomAt(seed, cellX & ~1, cellZ & ~1) != null;
    }

    // --- Big rooms (2x2 cells, chunk-aligned) ------------------------------

    /**
     * Big-room cells: the outer boundary keeps maze walls (raised to the
     * room's full height above door level), interior samples the template,
     * and the room gets its own ceiling at template height + 1.
     */
    private BlockState bigRoomState(long seed, RoomManager.LoadedRoom loadedRoom, int anchorX, int anchorZ,
                                    int x, int y, int z) {
        RoomGrid room = loadedRoom.grid();
        int superX = x - anchorX * CELL; // 0..15 within the 2x2 super-cell
        int superZ = z - anchorZ * CELL;
        if (superX == 0 || superZ == 0) {
            if (loadedRoom.definition().doors().isPresent()) {
                return sealedBoundaryState(seed, anchorX, anchorZ,
                        loadedRoom.definition().doors().get(), superX, superZ, y);
            }
            // Outer boundary: normal maze walls at door height, then solid
            // wallpaper all the way up (through the room's ceiling plane, so
            // the wall face never shows a tile stripe).
            boolean wall = y > WALL_MAX_Y || isWallColumn(seed, x, z);
            return wall ? NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        }
        int ceilingY = WALL_MIN_Y + room.sizeY();
        if (y == ceilingY) {
            if (Math.floorMod(x, CELL) == CELL / 2 && Math.floorMod(z, CELL) == CELL / 2) {
                return NoclipBlocks.FLUORESCENT_LIGHT.get().defaultBlockState();
            }
            return NoclipBlocks.STAINED_CEILING.get().defaultBlockState();
        }
        if (y > ceilingY) {
            return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        }
        BlockState state = room.sample(superX - 1, y - WALL_MIN_Y, superZ - 1,
                rotationAt(seed, anchorX, anchorZ, SALT_BIG_ROT));
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    private static Rotation rotationAt(long seed, int cellX, int cellZ, int salt) {
        return Rotation.values()[(int) (hash(seed, cellX, cellZ, salt) & 3)];
    }

    /**
     * Boundary of a sealed big room: solid wallpaper except exactly the
     * hash-chosen door segments. Handles the room-owned west/north edges;
     * {@link #segmentOpening} handles the east/south edges (owned by outside
     * cells) with the same door set.
     */
    private BlockState sealedBoundaryState(long seed, int anchorX, int anchorZ, int doorCount,
                                           int superX, int superZ, int y) {
        if (y > WALL_MAX_Y || (superX == 0 && superZ == 0)) {
            return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        }
        int segmentIndex;
        int along;
        if (superX == 0) {        // west side: segments 0..1
            segmentIndex = superZ / CELL;
            along = Math.floorMod(superZ, CELL);
        } else {                  // north side (superZ == 0): segments 2..3
            segmentIndex = 2 + superX / CELL;
            along = Math.floorMod(superX, CELL);
        }
        if (along != 0 && isDoorGap(along)
                && isSealedDoorSegment(seed, anchorX, anchorZ, doorCount, segmentIndex)) {
            return Blocks.AIR.defaultBlockState();
        }
        return NoclipBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
    }

    /**
     * Picks {@code doorCount} distinct segments out of the room's 8 perimeter
     * segments (west 0-1, north 2-3, east 4-5, south 6-7), seeded per anchor.
     */
    private boolean isSealedDoorSegment(long seed, int anchorX, int anchorZ, int doorCount, int segmentIndex) {
        long h = hash(seed, anchorX, anchorZ, SALT_SEALED_DOORS);
        boolean[] selected = new boolean[8];
        for (int i = 0; i < Math.min(doorCount, 8); i++) {
            int idx = (int) ((h >>> (i * 8)) & 0xFF) % 8;
            while (selected[idx]) {
                idx = (idx + 1) % 8;
            }
            selected[idx] = true;
        }
        return selected[segmentIndex];
    }

    /** The sealed big room covering this cell, or null. */
    @Nullable
    private RoomManager.LoadedRoom sealedRoomAt(long seed, int cellX, int cellZ) {
        RoomManager.LoadedRoom room = bigRoomAt(seed, cellX & ~1, cellZ & ~1);
        return room != null && room.definition().doors().isPresent() ? room : null;
    }

    // --- Layout decisions --------------------------------------------------

    private Zone zoneAt(long seed, int cellX, int cellZ) {
        int roll = (int) (hash(seed, Math.floorDiv(cellX, ZONE_CELLS), Math.floorDiv(cellZ, ZONE_CELLS), SALT_ZONE) & 0xFF);
        if (roll < 88) {          // ~34%: tight maze, mostly doorways, real dead ends
            return new Zone(false, 16, 26);
        } else if (roll < 176) {  // ~34%: mixed
            return new Zone(false, 46, 64);
        } else if (roll < 236) {  // ~23%: open halls, the classic pillar-field look
            return new Zone(false, 146, 192);
        } else {                  // ~8%: warehouse void
            return new Zone(true, 0, 0);
        }
    }

    private boolean isWallColumn(long seed, int x, int z) {
        int localX = Math.floorMod(x, CELL);
        int localZ = Math.floorMod(z, CELL);
        if (localX != 0 && localZ != 0) {
            return false;
        }
        if (localX == 0 && localZ == 0) {
            return true; // grid intersections are always pillars
        }
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        Opening opening = segmentOpening(seed, cellX, cellZ, localX == 0);
        return switch (opening) {
            case OPEN -> false;
            case SOLID -> true;
            case DOOR -> {
                int along = localX == 0 ? localZ : localX;
                yield !isDoorGap(along);
            }
        };
    }

    /**
     * Decides the wall segment on a cell's west edge ({@code west}) or north
     * edge. Layout is a binary-tree maze: every cell carves exactly one passage
     * (west or north, hash-chosen), which guarantees the infinite grid is fully
     * connected without any global computation, including across zone borders,
     * because a carve that crosses into a warehouse becomes a doorway rather
     * than being blocked. "Braid" rolls reopen extra walls so the maze has
     * loops, at a density set by the cell's zone.
     */
    private Opening segmentOpening(long seed, int cellX, int cellZ, boolean west) {
        // Sealed big rooms own their entire boundary: this branch covers their
        // east/south edges, whose columns belong to the outside cell.
        int neighborX = west ? cellX - 1 : cellX;
        int neighborZ = west ? cellZ : cellZ - 1;
        RoomManager.LoadedRoom neighborSealed = sealedRoomAt(seed, neighborX, neighborZ);
        if (neighborSealed != null) {
            int anchorX = neighborX & ~1;
            int anchorZ = neighborZ & ~1;
            int segmentIndex = west
                    ? 4 + (cellZ - anchorZ)   // room's east side
                    : 6 + (cellX - anchorX);  // room's south side
            return isSealedDoorSegment(seed, anchorX, anchorZ,
                    neighborSealed.definition().doors().orElse(1), segmentIndex)
                    ? Opening.DOOR : Opening.SOLID;
        }

        Zone zone = zoneAt(seed, cellX, cellZ);
        Zone neighborZone = west ? zoneAt(seed, cellX - 1, cellZ) : zoneAt(seed, cellX, cellZ - 1);
        boolean carvedHere = carvesWest(seed, cellX, cellZ) == west;

        if (zone.warehouse() && neighborZone.warehouse()) {
            return Opening.OPEN; // adjacent warehouses merge
        }
        if (zone.warehouse() != neighborZone.warehouse()) {
            // Warehouse cells never need their carve honored: their interior is
            // fully open, so the zone connects via the guaranteed side doors.
            // Only a normal cell wedged into a warehouse corner (both its
            // directions cross) keeps its passage as a door.
            if (carvedHere && !zone.warehouse()) {
                return Opening.DOOR;
            }
            // One guaranteed door per zone-border side, plus a rare extra.
            int along = west ? cellZ : cellX;
            if (Math.floorMod(along, ZONE_CELLS) == ZONE_CELLS / 2) {
                return Opening.DOOR;
            }
            return (hash(seed, cellX, cellZ, west ? SALT_BORDER : SALT_BORDER + 1) & 0xFF) < 10
                    ? Opening.DOOR : Opening.SOLID;
        }

        boolean carved = carvedHere
                || (hash(seed, cellX, cellZ, west ? SALT_BRAID : SALT_BRAID + 1) & 0xFF) < zone.braidChance();
        if (!carved) {
            return Opening.SOLID;
        }
        return (hash(seed, cellX, cellZ, west ? SALT_KIND : SALT_KIND + 1) & 0xFF) < zone.openKindChance()
                ? Opening.OPEN : Opening.DOOR;
    }

    /**
     * The cell's binary-tree carve direction, with border redirection: if the
     * carve would cross into a different zone type and the other direction
     * would not, it flips inward. The cell keeps exactly one guaranteed
     * passage (connectivity is unaffected); it just spends it inside its own
     * zone, so warehouse borders aren't riddled with forced doorways.
     */
    private boolean carvesWest(long seed, int cellX, int cellZ) {
        boolean carveWest = (hash(seed, cellX, cellZ, SALT_CARVE) & 1) == 0;
        boolean westCrosses = managedCrossing(seed, cellX, cellZ, cellX - 1, cellZ);
        boolean northCrosses = managedCrossing(seed, cellX, cellZ, cellX, cellZ - 1);
        if (carveWest && westCrosses && !northCrosses) {
            return false;
        }
        if (!carveWest && northCrosses && !westCrosses) {
            return true;
        }
        return carveWest;
    }

    /**
     * True when the wall between two cells is "managed" (warehouse border or
     * sealed-room boundary) rather than free maze: carves shouldn't be spent
     * crossing it.
     */
    private boolean managedCrossing(long seed, int cellX, int cellZ, int otherX, int otherZ) {
        if (zoneAt(seed, cellX, cellZ).warehouse() != zoneAt(seed, otherX, otherZ).warehouse()) {
            return true;
        }
        boolean selfSealed = sealedRoomAt(seed, cellX, cellZ) != null;
        boolean otherSealed = sealedRoomAt(seed, otherX, otherZ) != null;
        if (selfSealed != otherSealed) {
            return true;
        }
        // Two adjacent sealed rooms are distinct if their anchors differ.
        return selfSealed && ((cellX & ~1) != (otherX & ~1) || (cellZ & ~1) != (otherZ & ~1));
    }

    @Nullable
    private RoomGrid roomAt(long seed, int cellX, int cellZ) {
        List<RoomManager.LoadedRoom> rooms = RoomManager.smallRooms();
        if (rooms.isEmpty() || zoneAt(seed, cellX, cellZ).warehouse()) {
            return null;
        }
        long presence = hash(seed, cellX, cellZ, SALT_ROOM_PRESENCE);
        if ((presence & 0xFF) >= ROOM_CHANCE) {
            return null;
        }
        return weightedPick(rooms, RoomManager.smallTotalWeight(),
                hash(seed, cellX, cellZ, SALT_ROOM_PICK)).grid();
    }

    @Nullable
    private RoomManager.LoadedRoom bigRoomAt(long seed, int anchorX, int anchorZ) {
        List<RoomManager.LoadedRoom> rooms = RoomManager.bigRooms();
        if (rooms.isEmpty()) {
            return null;
        }
        // All four cells must be outside warehouse zones.
        if (zoneAt(seed, anchorX, anchorZ).warehouse() || zoneAt(seed, anchorX + 1, anchorZ).warehouse()
                || zoneAt(seed, anchorX, anchorZ + 1).warehouse() || zoneAt(seed, anchorX + 1, anchorZ + 1).warehouse()) {
            return null;
        }
        if ((hash(seed, anchorX, anchorZ, SALT_BIG_PRESENCE) & 0xFF) >= BIG_ROOM_CHANCE) {
            return null;
        }
        return weightedPick(rooms, RoomManager.bigTotalWeight(), hash(seed, anchorX, anchorZ, SALT_BIG_PICK));
    }

    private static RoomManager.LoadedRoom weightedPick(List<RoomManager.LoadedRoom> rooms, int totalWeight, long hash) {
        int pick = (int) Long.remainderUnsigned(hash, totalWeight);
        for (RoomManager.LoadedRoom room : rooms) {
            pick -= room.definition().weight();
            if (pick < 0) {
                return room;
            }
        }
        return rooms.get(rooms.size() - 1);
    }

    private static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ ((long) salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    // --- Block entities ----------------------------------------------------

    /**
     * Cells are 8 blocks, chunks 16, and big rooms anchor on even cells, so a
     * chunk is either exactly one big room or exactly four independent cells.
     * Either way all block entities land in this chunk.
     */
    private void placeRoomBlockEntities(long seed, ChunkPos chunkPos, ChunkAccess chunk) {
        int anchorX = Math.floorDiv(chunkPos.getMinBlockX(), CELL); // always even
        int anchorZ = Math.floorDiv(chunkPos.getMinBlockZ(), CELL);
        RoomManager.LoadedRoom bigRoom = bigRoomAt(seed, anchorX, anchorZ);
        if (bigRoom != null) {
            placeBlockEntities(bigRoom.grid(), anchorX, anchorZ, chunk,
                    rotationAt(seed, anchorX, anchorZ, SALT_BIG_ROT));
            return;
        }
        for (int cellX = anchorX; cellX <= anchorX + 1; cellX++) {
            for (int cellZ = anchorZ; cellZ <= anchorZ + 1; cellZ++) {
                RoomGrid room = roomAt(seed, cellX, cellZ);
                if (room != null) {
                    placeBlockEntities(room, cellX, cellZ, chunk, rotationAt(seed, cellX, cellZ, SALT_ROOM_ROT));
                }
            }
        }
    }

    private void placeBlockEntities(RoomGrid room, int cellX, int cellZ, ChunkAccess chunk, Rotation rotation) {
        for (RoomGrid.BlockEntityData blockEntity : room.blockEntities()) {
            CompoundTag tag = blockEntity.tag().copy();
            tag.putInt("x", cellX * CELL + 1 + room.rotatedX(blockEntity.x(), blockEntity.z(), rotation));
            tag.putInt("y", WALL_MIN_Y + blockEntity.y());
            tag.putInt("z", cellZ * CELL + 1 + room.rotatedZ(blockEntity.x(), blockEntity.z(), rotation));
            chunk.setBlockEntityNbt(tag);
        }
    }

    // --- Boilerplate -------------------------------------------------------

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // No carvers in the backrooms.
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                             ChunkAccess chunk) {
        // Everything is placed in fillFromNoise.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No ambient spawns during generation.
    }

    @Override
    public int getGenDepth() {
        return 64;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return WALL_MIN_Y; // stand on the carpet
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        long seed = layoutSeed(randomState);
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        return zoneAt(seed, cellX, cellZ).warehouse() ? WAREHOUSE_CEILING_Y + 1 : CEILING_Y + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        long seed = layoutSeed(randomState);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = stateAt(seed, x, level.getMinBuildHeight() + i, z);
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        long seed = layoutSeed(randomState);
        int cellX = Math.floorDiv(pos.getX(), CELL);
        int cellZ = Math.floorDiv(pos.getZ(), CELL);
        Zone zone = zoneAt(seed, cellX, cellZ);
        int roll = (int) (hash(seed, Math.floorDiv(cellX, ZONE_CELLS), Math.floorDiv(cellZ, ZONE_CELLS), SALT_ZONE) & 0xFF);
        String zoneName = zone.warehouse() ? "warehouse" : roll < 88 ? "maze" : roll < 176 ? "mixed" : "halls";
        info.add("Backrooms cell: " + cellX + ", " + cellZ + " (" + zoneName + ")");
    }
}
