package mekwasher.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mekanism.api.math.MathUtils;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.ModelRenderer;
import mekanism.client.render.RenderResizableCuboid;
import mekanism.client.render.tileentity.MekanismTileEntityRenderer;
import mekwasher.tile.TileEntityBulkWasher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Renders the water inside the glass section of the (Nutritional Liquifier style) model, and the item being washed
 * tumbling around in it while the machine runs.
 */
public class RenderBulkWasher extends MekanismTileEntityRenderer<TileEntityBulkWasher> {

    private static final int STAGES = 40;
    private static final Int2ObjectMap<MekanismRenderer.Model3D> CACHED_MODELS = new Int2ObjectOpenHashMap<>();

    public RenderBulkWasher(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void resetCachedModels() {
        CACHED_MODELS.clear();
    }

    @Override
    protected void render(TileEntityBulkWasher tile, float partialTick, PoseStack matrix, MultiBufferSource renderer, int light, int overlayLight,
          ProfilerFiller profiler) {
        if (!tile.waterTank.isEmpty()) {
            FluidStack water = tile.waterTank.getFluid();
            float scale = Math.min(1F, (float) water.getAmount() / tile.waterTank.getCapacity());
            MekanismRenderer.renderObject(getWaterModel(water, scale), matrix, renderer.getBuffer(Sheets.translucentCullBlockSheet()),
                  MekanismRenderer.getColorARGB(water, scale), light, overlayLight, RenderResizableCuboid.FaceDisplay.FRONT, getCamera(),
                  tile.getBlockPos());
        }

        ItemStack stack = tile.getRenderStack();
        if (!stack.isEmpty() && tile.getLevel() != null) {
            float time = tile.getLevel().getGameTime() + partialTick;
            matrix.pushPose();
            // Centered in the glass section (5px - 15px), submerged in the water
            matrix.translate(0.5, 0.5, 0.5);
            matrix.scale(1.5F, 1.5F, 1.5F);
            if (tile.getActive()) {
                // Tumble around like it's in a wash cycle
                matrix.translate(0, Mth.sin(time * 0.3F) * 0.03F, 0);
                matrix.mulPose(Axis.YP.rotationDegrees(time * 12F % 360F));
                matrix.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.2F) * 20F));
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, overlayLight, matrix, renderer,
                  tile.getLevel(), MathUtils.clampToInt(tile.getBlockPos().asLong()));
            matrix.popPose();
        }
    }

    @Override
    protected String getProfilerSection() {
        return "bulkWasher";
    }

    private MekanismRenderer.Model3D getWaterModel(FluidStack water, float scale) {
        int stage = ModelRenderer.getStage(water, STAGES, scale);
        MekanismRenderer.Model3D model = CACHED_MODELS.get(stage);
        if (model == null) {
            // Same bounds as the Nutritional Liquifier's paste: inside the glass, from the base (5px) to the lid (15px)
            model = new MekanismRenderer.Model3D()
                  .setTexture(MekanismRenderer.getFluidTexture(water, MekanismRenderer.FluidTextureType.STILL))
                  .setSideRender(Direction.DOWN, false)
                  .setSideRender(Direction.UP, stage < STAGES)
                  .xBounds(0.001F, 0.999F)
                  .yBounds(0.313F, 0.313F + 0.624F * (stage / (float) STAGES))
                  .zBounds(0.001F, 0.999F);
            CACHED_MODELS.put(stage, model);
        }
        return model;
    }
}
