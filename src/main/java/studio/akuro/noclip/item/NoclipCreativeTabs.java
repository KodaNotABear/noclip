package studio.akuro.noclip.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.akuro.noclip.Noclip;

import java.util.function.Supplier;

public class NoclipCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Noclip.MOD_ID);

    public static final Supplier<CreativeModeTab> NOCLIP_TAB = TABS.register("noclip", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.noclip"))
            .icon(() -> new ItemStack(NoclipItems.FLUORESCENT_LIGHT.get()))
            .displayItems((parameters, output) -> {
                output.accept(NoclipItems.YELLOW_WALLPAPER.get());
                output.accept(NoclipItems.STAINED_CEILING.get());
                output.accept(NoclipItems.DAMP_CARPET.get());
                output.accept(NoclipItems.FLUORESCENT_LIGHT.get());
                output.accept(NoclipItems.NOCLIP_KEY.get());
            })
            .build());
}
