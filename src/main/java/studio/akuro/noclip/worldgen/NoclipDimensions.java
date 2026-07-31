package studio.akuro.noclip.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import studio.akuro.noclip.Noclip;

public class NoclipDimensions {
    public static final ResourceKey<Level> BACKROOMS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Noclip.MOD_ID, "backrooms"));
}
