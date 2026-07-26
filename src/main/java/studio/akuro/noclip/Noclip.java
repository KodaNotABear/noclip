package studio.akuro.noclip;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import studio.akuro.noclip.block.NoclipBlocks;
import studio.akuro.noclip.item.NoclipCreativeTabs;
import studio.akuro.noclip.item.NoclipItems;
import studio.akuro.noclip.worldgen.NoclipWorldgen;

@Mod(Noclip.MOD_ID)
public class Noclip {
    public static final String MOD_ID = "noclip";

    public Noclip(IEventBus modEventBus) {
        NoclipBlocks.BLOCKS.register(modEventBus);
        NoclipItems.ITEMS.register(modEventBus);
        NoclipCreativeTabs.TABS.register(modEventBus);
        NoclipWorldgen.CHUNK_GENERATORS.register(modEventBus);
    }
}
