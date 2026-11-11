package mekwasher;

import mekwasher.registries.MWBlocks;
import mekwasher.registries.MWContainerTypes;
import mekwasher.registries.MWTileEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(MekWasher.MOD_ID)
public class MekWasher {

    public static final String MOD_ID = "mekwasher";

    private static final ResourceKey<CreativeModeTab> MEKANISM_TAB =
          ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("mekanism", "mekanism"));

    public MekWasher(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, WasherConfig.SPEC);
        MWBlocks.BLOCKS.register(modBus);
        MWTileEntityTypes.TILE_ENTITY_TYPES.register(modBus);
        MWContainerTypes.CONTAINER_TYPES.register(modBus);
        modBus.addListener(this::addToCreativeTabs);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(MEKANISM_TAB)) {
            event.accept(MWBlocks.BULK_WASHER);
        }
    }
}
