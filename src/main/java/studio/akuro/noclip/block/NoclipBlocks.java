package studio.akuro.noclip.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.akuro.noclip.Noclip;

/**
 * Level 0 palette. Structural blocks (wallpaper, ceiling, carpet) are
 * bedrock-tier unbreakable: the generated architecture is not a quarry.
 * Props (lights, and later furniture/fixtures) are breakable and form the
 * early-game resource base.
 */
public class NoclipBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Noclip.MOD_ID);

    private static BlockBehaviour.Properties structural(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .sound(sound);
    }

    public static final DeferredBlock<Block> YELLOW_WALLPAPER = BLOCKS.registerSimpleBlock("yellow_wallpaper",
            structural(MapColor.TERRACOTTA_YELLOW, SoundType.WOOD));

    public static final DeferredBlock<Block> STAINED_CEILING = BLOCKS.registerSimpleBlock("stained_ceiling",
            structural(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));

    public static final DeferredBlock<Block> DAMP_CARPET = BLOCKS.registerSimpleBlock("damp_carpet",
            structural(MapColor.COLOR_YELLOW, SoundType.WOOL));

    public static final DeferredBlock<FluorescentLightBlock> FLUORESCENT_LIGHT = BLOCKS.registerBlock("fluorescent_light",
            FluorescentLightBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(0.3F)
                    .lightLevel(state -> state.getValue(FluorescentLightBlock.LIT) ? 15 : 0)
                    .sound(SoundType.GLASS));
}
