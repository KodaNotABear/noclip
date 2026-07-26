package studio.akuro.noclip.worldgen.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One JSON entry in {@code data/<ns>/backrooms_rooms/}. The template id maps to
 * a vanilla structure NBT at {@code data/<ns>/structure/<path>.nbt}, so rooms
 * can be authored in-game with structure blocks and shipped in any datapack.
 */
public record RoomDefinition(ResourceLocation template, int weight, int cells, Optional<Integer> doors) {
    public static final Codec<RoomDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(RoomDefinition::template),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(RoomDefinition::weight),
            Codec.intRange(1, 2).optionalFieldOf("cells", 1).forGetter(RoomDefinition::cells),
            // Big rooms only: seal the outer boundary and punch exactly this
            // many doorways (seeded positions). Absent = normal maze walls.
            Codec.intRange(1, 8).optionalFieldOf("doors").forGetter(RoomDefinition::doors)
    ).apply(instance, RoomDefinition::new));
}
