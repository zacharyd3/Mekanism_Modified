package mekwasher.gametest;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekwasher.MekWasher;
import mekwasher.registries.MWBlocks;
import mekwasher.tile.TileEntityBulkWasher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dev-only automated checks (excluded from the mod jar). Run with: ./gradlew runGameTestServer
 */
@GameTestHolder(MekWasher.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = MekWasher.MOD_ID)
public class BulkWasherGameTests {

    private static final String TEMPLATE = "empty";
    private static final BlockPos WASHER = new BlockPos(1, 1, 1);

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(BulkWasherGameTests.class);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static TileEntityBulkWasher placeWasher(GameTestHelper helper) {
        helper.setBlock(WASHER, MWBlocks.BULK_WASHER.defaultState());
        TileEntityBulkWasher washer = (TileEntityBulkWasher) helper.getBlockEntity(WASHER);
        // Same as a player placing the item: applies the default side config (energy all sides, water on top)
        washer.applyComponentsFromItemStack(new ItemStack(MWBlocks.BULK_WASHER.asItem()));
        return washer;
    }

    /** FE goes in from any side, water can be piped in from the top, then crushed iron washes into nuggets. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void washesCrushedIronWithFeAndPipedWater(GameTestHelper helper) {
        TileEntityBulkWasher washer = placeWasher(helper);
        BlockPos abs = helper.absolutePos(WASHER);

        IEnergyStorage fe = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, Direction.NORTH);
        helper.assertTrue(fe != null, "No FE capability exposed");
        int accepted = fe.receiveEnergy(1_000_000, false);
        helper.assertTrue(accepted > 0, "Washer did not accept FE");

        IFluidHandler topFluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.UP);
        helper.assertTrue(topFluid != null, "No fluid capability on top face");
        int filled = topFluid.fill(new FluidStack(Fluids.WATER, 5_000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == 5_000, "Expected 5000 mB water accepted from top, got " + filled);
        int lava = topFluid.fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.SIMULATE);
        helper.assertTrue(lava == 0, "Washer should reject non-water fluids");

        List<IInventorySlot> slots = washer.getInventorySlots(null);
        ItemStack crushedIron = new ItemStack(item("create:crushed_raw_iron"), 2);
        ItemStack remainder = slots.get(0).insertItem(crushedIron, Action.EXECUTE, AutomationType.EXTERNAL);
        helper.assertTrue(remainder.isEmpty(), "Input slot rejected crushed raw iron");
        ItemStack dirt = slots.get(0).insertItem(new ItemStack(Items.DIRT), Action.SIMULATE, AutomationType.EXTERNAL);
        helper.assertTrue(!dirt.isEmpty(), "Input slot should reject items without a washing recipe");

        helper.succeedWhen(() -> {
            int nuggets = 0;
            for (int i = 1; i <= TileEntityBulkWasher.OUTPUT_SLOTS; i++) {
                ItemStack out = slots.get(i).getStack();
                if (out.is(Items.IRON_NUGGET)) {
                    nuggets += out.getCount();
                }
            }
            helper.assertTrue(nuggets == 18, "Expected 18 iron nuggets, have " + nuggets);
            helper.assertTrue(slots.get(0).isEmpty(), "Input not consumed");
            helper.assertTrue(washer.waterTank.getFluidAmount() == 5_000 - 2 * 50, "Water not consumed: " + washer.waterTank.getFluidAmount());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void doesNothingWithoutWater(GameTestHelper helper) {
        TileEntityBulkWasher washer = placeWasher(helper);
        washer.getEnergyContainer().insert(Long.MAX_VALUE, Action.EXECUTE, AutomationType.INTERNAL);
        List<IInventorySlot> slots = washer.getInventorySlots(null);
        slots.get(0).insertItem(new ItemStack(Items.GRAVEL, 4), Action.EXECUTE, AutomationType.EXTERNAL);
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(slots.get(0).getCount() == 4, "Washed without water!");
            helper.assertTrue(washer.hasNotEnoughWater(), "Missing-water warning not set");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void speedUpgradesReduceTicks(GameTestHelper helper) {
        TileEntityBulkWasher washer = placeWasher(helper);
        int base = washer.ticksRequired;
        washer.getComponent().addUpgrades(Upgrade.SPEED, 8);
        helper.assertTrue(washer.ticksRequired < base, "Speed upgrades did not reduce ticks (" + base + " -> " + washer.ticksRequired + ")");
        // Speed upgrades raise energy use a lot (as in Mekanism), so keep it topped up like a cable would
        helper.onEachTick(() -> washer.getEnergyContainer().insert(Long.MAX_VALUE, Action.EXECUTE, AutomationType.INTERNAL));
        washer.waterTank.insert(new FluidStack(Fluids.WATER, 10_000), Action.EXECUTE, AutomationType.INTERNAL);
        List<IInventorySlot> slots = washer.getInventorySlots(null);
        slots.get(0).insertItem(new ItemStack(Items.GRAVEL, 8), Action.EXECUTE, AutomationType.EXTERNAL);
        helper.succeedWhen(() -> helper.assertTrue(slots.get(0).isEmpty(), "Upgraded washer has not finished 8 gravel yet"));
    }
}
