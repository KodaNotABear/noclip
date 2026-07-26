package studio.akuro.noclip.worldgen.room;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads room definitions from {@code data/<ns>/backrooms_rooms/*.json} on every
 * datapack (re)load. Any namespace works, so compat patches and packs can add
 * rooms without touching this mod.
 */
@EventBusSubscriber(modid = "noclip")
public class RoomManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "backrooms_rooms";

    private static volatile List<LoadedRoom> smallRooms = List.of();
    private static volatile List<LoadedRoom> bigRooms = List.of();
    private static volatile int smallTotalWeight = 0;
    private static volatile int bigTotalWeight = 0;

    private final HolderGetter<Block> blockLookup;

    private RoomManager(HolderGetter<Block> blockLookup) {
        super(GSON, DIRECTORY);
        this.blockLookup = blockLookup;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RoomManager(
                event.getRegistryAccess().registryOrThrow(Registries.BLOCK).asLookup()));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<LoadedRoom> small = new ArrayList<>();
        List<LoadedRoom> big = new ArrayList<>();
        // Sorted ids keep the weighted-pick order stable across restarts, so the
        // same seed always produces the same rooms.
        List<ResourceLocation> ids = new ArrayList<>(jsons.keySet());
        Collections.sort(ids);
        for (ResourceLocation id : ids) {
            try {
                RoomDefinition definition = RoomDefinition.CODEC
                        .parse(JsonOps.INSTANCE, jsons.get(id))
                        .getOrThrow(IllegalStateException::new);
                RoomGrid grid = RoomGrid.load(resourceManager, definition.template(), blockLookup,
                        definition.cells());
                (definition.cells() == 2 ? big : small).add(new LoadedRoom(id, definition, grid));
            } catch (Exception e) {
                LOGGER.error("Skipping backrooms room {}: {}", id, e.getMessage());
            }
        }
        smallRooms = List.copyOf(small);
        bigRooms = List.copyOf(big);
        smallTotalWeight = small.stream().mapToInt(room -> room.definition().weight()).sum();
        bigTotalWeight = big.stream().mapToInt(room -> room.definition().weight()).sum();
        LOGGER.info("Loaded {} backrooms rooms ({} single-cell, {} big)",
                small.size() + big.size(), small.size(), big.size());
    }

    public static List<LoadedRoom> smallRooms() {
        return smallRooms;
    }

    public static List<LoadedRoom> bigRooms() {
        return bigRooms;
    }

    public static int smallTotalWeight() {
        return smallTotalWeight;
    }

    public static int bigTotalWeight() {
        return bigTotalWeight;
    }

    public record LoadedRoom(ResourceLocation id, RoomDefinition definition, RoomGrid grid) {
    }
}
