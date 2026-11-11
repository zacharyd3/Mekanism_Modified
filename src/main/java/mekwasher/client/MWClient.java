package mekwasher.client;

import mekanism.client.ClientRegistrationUtil;
import mekwasher.MekWasher;
import mekwasher.registries.MWContainerTypes;
import mekwasher.registries.MWTileEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

@EventBusSubscriber(modid = MekWasher.MOD_ID, value = Dist.CLIENT)
public final class MWClient {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        ClientRegistrationUtil.registerScreen(event, MWContainerTypes.BULK_WASHER, GuiBulkWasher::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MWTileEntityTypes.BULK_WASHER.get(), RenderBulkWasher::new);
    }

    @SubscribeEvent
    public static void onStitch(TextureAtlasStitchedEvent event) {
        // Fluid sprites may change on resource reload
        RenderBulkWasher.resetCachedModels();
    }

    private MWClient() {
    }
}
