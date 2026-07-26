package studio.akuro.noclip.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.akuro.noclip.Noclip;

import java.util.function.Supplier;

public class NoclipWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Noclip.MOD_ID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> BACKROOMS =
            CHUNK_GENERATORS.register("backrooms", () -> BackroomsChunkGenerator.CODEC);
}
