package studio.akuro.noclip.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import studio.akuro.noclip.Noclip;

/**
 * Vanilla spawn placement scans heightmaps, which would put players on top of
 * the sealed ceiling. Cell centers are guaranteed open floor (walls only sit
 * on grid lines), so a fixed cell-center spawn is always safe.
 */
@EventBusSubscriber(modid = Noclip.MOD_ID)
public class BackroomsSpawnHandler {

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.getChunkSource().getGenerator() instanceof BackroomsChunkGenerator) {
            event.getSettings().setSpawn(new BlockPos(4, 1, 4), 0.0F);
            event.setCanceled(true);
        }
    }
}
