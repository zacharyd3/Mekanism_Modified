package mekwasher.gametest;

import java.io.File;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekwasher.MekWasher;
import mekwasher.registries.MWBlocks;
import mekwasher.tile.TileEntityBulkWasher;
import mekwasher.tile.TileEntityElectricDeployer;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Dev-only (excluded from the mod jar): when MEKWASHER_SCREENSHOT=1, creates a flat world, sets up running washers,
 * takes screenshots of the block and its GUI into run/screenshots, then quits.
 */
@EventBusSubscriber(modid = MekWasher.MOD_ID, value = Dist.CLIENT)
public final class ScreenshotDriver {

    private static final boolean ENABLED = "1".equals(System.getenv("MEKWASHER_SCREENSHOT"));
    private static boolean worldRequested;
    private static int ticksInWorld;
    private static int menuTicks;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && (mc.screen instanceof TitleScreen || mc.screen != null && mc.getOverlay() == null && ++menuTicks > 100)) {
                worldRequested = true;
                LevelSettings settings = new LevelSettings("washer_test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                      new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("washer_test_" + System.currentTimeMillis(), settings, new WorldOptions(1L, false, false),
                      access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                      mc.screen);
            }
            return;
        }
        ticksInWorld++;
        if (ticksInWorld == 40) {
            mc.getSingleplayerServer().execute(ScreenshotDriver::setupScene);
        } else if (ticksInWorld == 200) {
            mc.options.hideGui = true;
        } else if (ticksInWorld == 205) {
            shot(mc, "washer_world.png");
        } else if (ticksInWorld == 215) {
            mc.options.hideGui = false;
            mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                ServerLevel level = player.serverLevel();
                if (level.getBlockEntity(washerPos(player)) instanceof TileEntityBulkWasher washer) {
                    washer.openGui(player);
                }
            });
        } else if (ticksInWorld == 260) {
            shot(mc, "washer_gui.png");
        } else if (ticksInWorld == 270) {
            mc.player.closeContainer();
        } else if (ticksInWorld == 280) {
            mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                if (player.serverLevel().getBlockEntity(washerPos(player).east()) instanceof TileEntityElectricDeployer deployer) {
                    deployer.openGui(player);
                }
            });
        } else if (ticksInWorld == 325) {
            shot(mc, "deployer_gui.png");
        } else if (ticksInWorld == 340) {
            mc.stop();
        }
    }

    private static BlockPos washerPos(ServerPlayer player) {
        return new BlockPos(0, -60, 0);
    }

    private static void setupScene() {
        Minecraft mc = Minecraft.getInstance();
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        BlockPos main = washerPos(player);
        // Left to right on screen: two deployers (cogwheels, precision mechanism step), running washer, half-full idle washer
        setupWasher(level, main, 8000, new ItemStack(item("create:crushed_raw_iron"), 64));
        setupWasher(level, main.west(), 4000, ItemStack.EMPTY);
        setupDeployer(level, main.east(), new ItemStack(item("create:shaft"), 64), new ItemStack(Items.OAK_PLANKS, 64));
        setupDeployer(level, main.east(2), new ItemStack(item("create:golden_sheet"), 64), new ItemStack(item("create:cogwheel"), 64));
        for (int x = -1; x <= 2; x++) {
            level.setBlockAndUpdate(main.offset(x, -1, 0), Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        // Stand in front (north side = front) looking at them
        player.teleportTo(level, 1.0, -59.0, -3.3, 0F, 25F);
    }

    private static void setupWasher(ServerLevel level, BlockPos pos, int water, ItemStack input) {
        level.setBlockAndUpdate(pos, MWBlocks.BULK_WASHER.defaultState());
        if (level.getBlockEntity(pos) instanceof TileEntityBulkWasher washer) {
            washer.applyComponentsFromItemStack(new ItemStack(MWBlocks.BULK_WASHER.asItem()));
            washer.getEnergyContainer().insert(Long.MAX_VALUE, Action.EXECUTE, AutomationType.INTERNAL);
            if (water > 0) {
                washer.waterTank.insert(new FluidStack(Fluids.WATER, water), Action.EXECUTE, AutomationType.INTERNAL);
            }
            if (!input.isEmpty()) {
                washer.getInventorySlots(null).get(0).setStack(input);
            }
        }
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static void setupDeployer(ServerLevel level, BlockPos pos, ItemStack input, ItemStack held) {
        level.setBlockAndUpdate(pos, MWBlocks.ELECTRIC_DEPLOYER.defaultState());
        if (level.getBlockEntity(pos) instanceof TileEntityElectricDeployer deployer) {
            deployer.applyComponentsFromItemStack(new ItemStack(MWBlocks.ELECTRIC_DEPLOYER.asItem()));
            deployer.getEnergyContainer().insert(Long.MAX_VALUE, Action.EXECUTE, AutomationType.INTERNAL);
            deployer.getInventorySlots(null).get(0).setStack(input);
            deployer.getInventorySlots(null).get(1).setStack(held);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(new File(mc.gameDirectory.getAbsolutePath()), name, mc.getMainRenderTarget(),
              msg -> System.out.println("Screenshot: " + msg.getString()));
    }

    private ScreenshotDriver() {
    }
}
