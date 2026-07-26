package studio.akuro.noclip.worldgen.room;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A room interior decoded from structure NBT. Single-cell rooms are capped at
 * 7x4x7 (the space between a cell's boundary walls); big 2x2-cell rooms at
 * 15x12x15. A null state means "leave whatever the base generator produces"
 * (structure void); explicit air clears. Templates smaller than the cap are
 * padded with nulls, and the template's height determines where the generator
 * puts the room's ceiling.
 */
public final class RoomGrid {
    public static final int SMALL_SIZE = 7;
    public static final int SMALL_HEIGHT = 4;
    public static final int BIG_SIZE = 15;
    public static final int BIG_HEIGHT = 12;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    /** Max index of the (square) cell-interior footprint the room rotates within. */
    private final int span;
    private final BlockState[] states;
    private final List<BlockEntityData> blockEntities = new ArrayList<>();

    public record BlockEntityData(int x, int y, int z, CompoundTag tag) {
    }

    private RoomGrid(int sizeX, int sizeY, int sizeZ, int span) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.span = span;
        this.states = new BlockState[sizeX * sizeY * sizeZ];
    }

    public static RoomGrid load(ResourceManager resourceManager, ResourceLocation template,
                                HolderGetter<Block> blockLookup, int cells) throws IOException {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                template.getNamespace(), "structure/" + template.getPath() + ".nbt");
        CompoundTag root;
        try (InputStream in = resourceManager.getResourceOrThrow(file).open()) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        int maxSize = cells == 2 ? BIG_SIZE : SMALL_SIZE;
        int maxHeight = cells == 2 ? BIG_HEIGHT : SMALL_HEIGHT;
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        int sx = sizeTag.getInt(0);
        int sy = sizeTag.getInt(1);
        int sz = sizeTag.getInt(2);
        if (sx > maxSize || sy > maxHeight || sz > maxSize) {
            throw new IOException("Room template " + template + " is " + sx + "x" + sy + "x" + sz
                    + " but " + cells + "-cell rooms are capped at " + maxSize + "x" + maxHeight + "x" + maxSize);
        }

        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            if ("minecraft:structure_void".equals(entry.getString("Name"))) {
                palette[i] = null;
            } else {
                palette[i] = NbtUtils.readBlockState(blockLookup, entry);
            }
        }

        RoomGrid grid = new RoomGrid(sx, sy, sz, maxSize - 1);
        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag entry = blocksTag.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            BlockState state = palette[entry.getInt("state")];
            if (state == null) {
                continue;
            }
            grid.states[grid.index(x, y, z)] = state;
            if (entry.contains("nbt", Tag.TAG_COMPOUND)) {
                grid.blockEntities.add(new BlockEntityData(x, y, z, entry.getCompound("nbt").copy()));
            }
        }
        return grid;
    }

    private int index(int x, int y, int z) {
        return (y * sizeX + x) * sizeZ + z;
    }

    /**
     * Coordinates are room-local; out-of-template positions (a template smaller
     * than its cap) and structure voids both return null = defer to the base
     * generator.
     */
    @Nullable
    public BlockState get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return null;
        }
        return states[index(x, y, z)];
    }

    /**
     * Samples the room as if the template were rotated by {@code rotation}
     * around the center of its square footprint (vanilla's transform
     * convention, so facing blocks rotate correctly).
     */
    @Nullable
    public BlockState sample(int x, int y, int z, Rotation rotation) {
        int templateX = switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> z;
            case CLOCKWISE_180 -> span - x;
            case COUNTERCLOCKWISE_90 -> span - z;
        };
        int templateZ = switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> span - x;
            case CLOCKWISE_180 -> span - z;
            case COUNTERCLOCKWISE_90 -> x;
        };
        BlockState state = get(templateX, y, templateZ);
        return state == null || rotation == Rotation.NONE ? state : state.rotate(rotation);
    }

    /** Forward transform of a template-local x to room-local, for block entities. */
    public int rotatedX(int x, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> span - z;
            case CLOCKWISE_180 -> span - x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    /** Forward transform of a template-local z to room-local, for block entities. */
    public int rotatedZ(int x, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> span - z;
            case COUNTERCLOCKWISE_90 -> span - x;
        };
    }

    /** Template height; the generator places the room's ceiling right above it. */
    public int sizeY() {
        return sizeY;
    }

    public List<BlockEntityData> blockEntities() {
        return blockEntities;
    }
}
