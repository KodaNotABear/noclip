package studio.akuro.noclip.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import studio.akuro.noclip.worldgen.NoclipDimensions;

public class NoclipKeyItem extends Item {
    private static final BlockPos BACKROOMS_ENTRY = new BlockPos(4, 1, 4);

    public NoclipKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, 40);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        ServerLevel target = targetLevel(serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(Component.translatable("item.noclip.noclip_key.missing_dimension"), true);
            return InteractionResultHolder.fail(stack);
        }

        serverPlayer.stopRiding();
        if (target.dimension() == NoclipDimensions.BACKROOMS) {
            serverPlayer.teleportTo(target, BACKROOMS_ENTRY.getX() + 0.5, BACKROOMS_ENTRY.getY(),
                    BACKROOMS_ENTRY.getZ() + 0.5, serverPlayer.getYRot(), serverPlayer.getXRot());
        } else {
            BlockPos spawn = target.getSharedSpawnPos();
            serverPlayer.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    target.getSharedSpawnAngle(), 0.0F);
        }

        target.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.65F, 0.75F);
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }

    private static ServerLevel targetLevel(ServerPlayer player) {
        if (player.level().dimension() == NoclipDimensions.BACKROOMS) {
            return player.server.overworld();
        }
        return player.server.getLevel(NoclipDimensions.BACKROOMS);
    }
}
