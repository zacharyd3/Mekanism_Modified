package mekwasher.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mekanism.api.math.MathUtils;
import mekanism.client.render.tileentity.MekanismTileEntityRenderer;
import mekwasher.tile.TileEntityElectricDeployer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Inside the glass: the processed item lies on the floor, the held item hangs above it and presses down while running,
 * like Create's deployer hand.
 */
public class RenderElectricDeployer extends MekanismTileEntityRenderer<TileEntityElectricDeployer> {

    public RenderElectricDeployer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(TileEntityElectricDeployer tile, float partialTick, PoseStack matrix, MultiBufferSource renderer, int light, int overlayLight,
          ProfilerFiller profiler) {
        if (tile.getLevel() == null) {
            return;
        }
        int seed = MathUtils.clampToInt(tile.getBlockPos().asLong());
        ItemStack input = tile.getRenderInput();
        if (!input.isEmpty()) {
            matrix.pushPose();
            // Lying flat on the floor of the glass section (5px)
            matrix.translate(0.5, 5.2 / 16, 0.5);
            matrix.mulPose(Axis.XP.rotationDegrees(90));
            matrix.scale(0.7F, 0.7F, 0.7F);
            Minecraft.getInstance().getItemRenderer().renderStatic(input, ItemDisplayContext.FIXED, light, overlayLight, matrix, renderer,
                  tile.getLevel(), seed);
            matrix.popPose();
        }
        ItemStack held = tile.getRenderHeld();
        if (!held.isEmpty()) {
            float press = 0;
            if (tile.getActive()) {
                // Press cycle: quick stamp down, slower lift
                float time = (tile.getLevel().getGameTime() + partialTick) % 20 / 20F;
                press = time < 0.3F ? time / 0.3F : 1 - (time - 0.3F) / 0.7F;
                press = Mth.clamp(press, 0, 1);
            }
            matrix.pushPose();
            matrix.translate(0.5, 0.78 - press * 0.3, 0.5);
            matrix.mulPose(Axis.XP.rotationDegrees(90));
            matrix.scale(0.6F, 0.6F, 0.6F);
            Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.FIXED, light, overlayLight, matrix, renderer,
                  tile.getLevel(), seed + 1);
            matrix.popPose();
        }
    }

    @Override
    protected String getProfilerSection() {
        return "electricDeployer";
    }
}
