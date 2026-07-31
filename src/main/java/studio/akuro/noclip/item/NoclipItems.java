package studio.akuro.noclip.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.akuro.noclip.Noclip;
import studio.akuro.noclip.block.NoclipBlocks;

public class NoclipItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Noclip.MOD_ID);

    public static final DeferredItem<BlockItem> YELLOW_WALLPAPER =
            ITEMS.registerSimpleBlockItem(NoclipBlocks.YELLOW_WALLPAPER);
    public static final DeferredItem<BlockItem> STAINED_CEILING =
            ITEMS.registerSimpleBlockItem(NoclipBlocks.STAINED_CEILING);
    public static final DeferredItem<BlockItem> DAMP_CARPET =
            ITEMS.registerSimpleBlockItem(NoclipBlocks.DAMP_CARPET);
    public static final DeferredItem<BlockItem> FLUORESCENT_LIGHT =
            ITEMS.registerSimpleBlockItem(NoclipBlocks.FLUORESCENT_LIGHT);

    public static final DeferredItem<Item> NOCLIP_KEY = ITEMS.register("noclip_key",
            () -> new NoclipKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
}
