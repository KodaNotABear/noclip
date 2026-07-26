package studio.akuro.noclip.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ceiling panel with a lit/unlit state, like a redstone lamp. The generator
 * places dead panels as {@code lit=false}; scripted events (blackouts,
 * flicker) can toggle the property in place without changing block identity.
 */
public class FluorescentLightBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FluorescentLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
