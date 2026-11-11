package mekwasher.tile;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.Upgrade;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UpgradeUtils;
import mekwasher.WasherConfig;
import mekwasher.registries.MWBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single-block Mekanism machine that performs Create's "Bulk Washing" (create:splashing recipes) using energy and water.
 * <p>
 * Built on Mekanism's own machine framework, so it gets the standard Mekanism GUI, side configuration, auto-eject,
 * redstone control, security, energy item slot and Speed / Energy / Muffling upgrades.
 */
public class TileEntityBulkWasher extends TileEntityConfigurableMachine {

    public static final int OUTPUT_SLOTS = 4;

    // NOTE: these fields are assigned from inside the super constructor (getInitial* methods),
    // so they must NOT have field initializers or they would be overwritten afterwards.
    private MachineEnergyContainer<TileEntityBulkWasher> energyContainer;
    public IExtendedFluidTank waterTank;
    private InputInventorySlot inputSlot;
    private List<OutputInventorySlot> outputSlots;
    private FluidInventorySlot waterFillSlot;
    private OutputInventorySlot waterContainerOutputSlot;
    private EnergyInventorySlot energySlot;

    private int operatingTicks;
    private int baseTicksRequired;
    public int ticksRequired;
    private int operationsPerTick;
    private boolean noOutputSpace;
    private boolean notEnoughEnergy;
    private boolean notEnoughWater;

    // Client render state (synced through the update tag): the item currently being washed + water level
    private ItemStack renderStack = ItemStack.EMPTY;
    private float lastWaterScale;

    // Recipe lookup cache so we don't scan the recipe manager every tick
    private ItemStack cachedInput = ItemStack.EMPTY;
    @Nullable
    private RecipeHolder<SplashingRecipe> cachedRecipe;

    public TileEntityBulkWasher(BlockPos pos, BlockState state) {
        super(MWBlocks.BULK_WASHER, pos, state);
        baseTicksRequired = WasherConfig.get(WasherConfig.TICKS_PER_OPERATION);
        ticksRequired = baseTicksRequired;
        operationsPerTick = 1;
        List<IInventorySlot> outputs = new ArrayList<>(outputSlots);
        outputs.add(waterContainerOutputSlot);
        configComponent.setupItemIOConfig(List.of(inputSlot, waterFillSlot), outputs, energySlot, false);
        configComponent.setupInputConfig(TransmissionType.FLUID, waterTank);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    public static boolean isWater(FluidStack stack) {
        return stack.is(FluidTags.WATER);
    }

    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        builder.addTank(waterTank = BasicFluidTank.input(WasherConfig.get(WasherConfig.WATER_CAPACITY), TileEntityBulkWasher::isWater, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        // Layout mirrors Mekanism's Nutritional Liquifier GUI
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(TileEntityBulkWasher::isWashable, listener, 26, 36));
        outputSlots = new ArrayList<>(OUTPUT_SLOTS);
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            OutputInventorySlot outputSlot = OutputInventorySlot.at(listener, 86 + (i % 2) * 18, 27 + (i / 2) * 18);
            builder.addSlot(outputSlot).tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, () -> noOutputSpace));
            outputSlots.add(outputSlot);
        }
        builder.addSlot(waterFillSlot = FluidInventorySlot.fill(waterTank, listener, 155, 25));
        builder.addSlot(waterContainerOutputSlot = OutputInventorySlot.at(listener, 155, 56));
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 155, 5));
        waterFillSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        waterFillSlot.fillTank(waterContainerOutputSlot);

        boolean active = false;
        boolean blockedOnSpace = false;
        boolean blockedOnEnergy = false;
        boolean blockedOnWater = false;
        ItemStack input = inputSlot.getStack();
        RecipeHolder<SplashingRecipe> recipe = input.isEmpty() ? null : getRecipe(input);
        if (recipe == null) {
            if (operatingTicks != 0) {
                operatingTicks = 0;
                markForSave();
            }
        } else if (!hasWaterFor(1)) {
            blockedOnWater = true;
        } else if (!canFitAllOutputs(recipe.value())) {
            blockedOnSpace = true;
        } else if (canFunction()) {
            long energyPerTick = energyContainer.getEnergyPerTick();
            if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL) < energyPerTick) {
                blockedOnEnergy = true;
            } else {
                energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                active = true;
                operatingTicks++;
                if (operatingTicks >= ticksRequired) {
                    operatingTicks = 0;
                    // With enough speed upgrades Mekanism machines can do more than one operation per tick
                    for (int i = 0; i < operationsPerTick; i++) {
                        if (!washOne()) {
                            break;
                        }
                    }
                }
                markForSave();
            }
        }
        noOutputSpace = blockedOnSpace;
        notEnoughEnergy = blockedOnEnergy;
        notEnoughWater = blockedOnWater;
        setActive(active);

        // Keep clients' view of the water level and the item inside the glass up to date
        ItemStack newRenderStack = recipe == null ? ItemStack.EMPTY : input.copyWithCount(1);
        if (!ItemStack.isSameItemSameComponents(newRenderStack, renderStack)) {
            renderStack = newRenderStack;
            sendUpdatePacket = true;
        }
        float waterScale = MekanismUtils.getScale(lastWaterScale, waterTank);
        if (MekanismUtils.scaleChanged(waterScale, lastWaterScale)) {
            lastWaterScale = waterScale;
            sendUpdatePacket = true;
        }
        return sendUpdatePacket;
    }

    private boolean hasWaterFor(int operations) {
        int perOperation = WasherConfig.get(WasherConfig.WATER_PER_OPERATION);
        if (perOperation <= 0) {
            return !waterTank.isEmpty();
        }
        return waterTank.getFluidAmount() >= (long) perOperation * operations;
    }

    /** Washes a single input item. Returns false if nothing could be washed. */
    private boolean washOne() {
        ItemStack input = inputSlot.getStack();
        if (input.isEmpty() || level == null || !hasWaterFor(1)) {
            return false;
        }
        RecipeHolder<SplashingRecipe> recipe = getRecipe(input);
        if (recipe == null || !canFitAllOutputs(recipe.value())) {
            return false;
        }
        for (ItemStack result : recipe.value().rollResults(level.getRandom())) {
            ItemStack remainder = result;
            for (OutputInventorySlot slot : outputSlots) {
                remainder = slot.insertItem(remainder, Action.EXECUTE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
        inputSlot.extractItem(1, Action.EXECUTE, AutomationType.INTERNAL);
        int water = WasherConfig.get(WasherConfig.WATER_PER_OPERATION);
        if (water > 0) {
            waterTank.extract(water, Action.EXECUTE, AutomationType.INTERNAL);
        }
        return true;
    }

    /**
     * Checks the outputs would fit even if every chance-based result succeeds, so items are never voided.
     */
    private boolean canFitAllOutputs(SplashingRecipe recipe) {
        List<ItemStack> simulated = new ArrayList<>(outputSlots.size());
        for (OutputInventorySlot slot : outputSlots) {
            simulated.add(slot.getStack().copy());
        }
        for (ProcessingOutput output : recipe.getRollableResults()) {
            ItemStack toInsert = output.getStack().copy();
            for (int i = 0; i < simulated.size() && !toInsert.isEmpty(); i++) {
                ItemStack existing = simulated.get(i);
                int limit = Math.min(toInsert.getMaxStackSize(), outputSlots.get(i).getLimit(toInsert));
                if (existing.isEmpty()) {
                    int moved = Math.min(limit, toInsert.getCount());
                    simulated.set(i, toInsert.copyWithCount(moved));
                    toInsert.shrink(moved);
                } else if (ItemStack.isSameItemSameComponents(existing, toInsert)) {
                    int moved = Math.min(limit - existing.getCount(), toInsert.getCount());
                    if (moved > 0) {
                        existing.grow(moved);
                        toInsert.shrink(moved);
                    }
                }
            }
            if (!toInsert.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private RecipeHolder<SplashingRecipe> getRecipe(ItemStack input) {
        if (!ItemStack.isSameItemSameComponents(input, cachedInput)) {
            cachedInput = input.copyWithCount(1);
            cachedRecipe = level == null ? null
                                         : AllRecipeTypes.SPLASHING.<SingleRecipeInput, SplashingRecipe>find(new SingleRecipeInput(cachedInput), level).orElse(null);
        }
        return cachedRecipe;
    }

    /**
     * Whether Create has a washing recipe for the stack. Used for slot validation, so it has to work without a tile level.
     */
    public static boolean isWashable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = findAnyLevel();
        if (level == null) {
            // Can't check recipes yet (e.g. during world load); don't reject anything
            return true;
        }
        return AllRecipeTypes.SPLASHING.find(new SingleRecipeInput(stack.copyWithCount(1)), level).isPresent();
    }

    @Nullable
    private static Level findAnyLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.overworld();
        }
        if (FMLEnvironment.dist.isClient()) {
            return mekwasher.client.ClientLevelAccess.getClientLevel();
        }
        return null;
    }

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        super.recalculateUpgrades(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, baseTicksRequired);
            operationsPerTick = MekanismUtils.getOperationsPerTick(this, baseTicksRequired, 1);
        }
    }

    @NotNull
    @Override
    public List<Component> getInfo(@NotNull Upgrade upgrade) {
        return UpgradeUtils.getMultScaledInfo(this, upgrade);
    }

    public double getScaledProgress() {
        return ticksRequired <= 0 ? 0 : (double) operatingTicks / ticksRequired;
    }

    public boolean hasNoOutputSpace() {
        return noOutputSpace;
    }

    public boolean hasNotEnoughEnergy() {
        return notEnoughEnergy;
    }

    public boolean hasNotEnoughWater() {
        return notEnoughWater;
    }

    public ItemStack getRenderStack() {
        return renderStack;
    }

    public MachineEnergyContainer<TileEntityBulkWasher> getEnergyContainer() {
        return energyContainer;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> operatingTicks, value -> operatingTicks = value));
        container.track(SyncableInt.create(() -> ticksRequired, value -> ticksRequired = value));
        container.track(SyncableBoolean.create(() -> noOutputSpace, value -> noOutputSpace = value));
        container.track(SyncableBoolean.create(() -> notEnoughEnergy, value -> notEnoughEnergy = value));
        container.track(SyncableBoolean.create(() -> notEnoughWater, value -> notEnoughWater = value));
    }

    @NotNull
    @Override
    public CompoundTag getReducedUpdateTag(@NotNull HolderLookup.Provider provider) {
        CompoundTag updateTag = super.getReducedUpdateTag(provider);
        updateTag.put("water", waterTank.serializeNBT(provider));
        if (!renderStack.isEmpty()) {
            updateTag.put("item", renderStack.save(provider));
        }
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        if (tag.contains("water")) {
            waterTank.deserializeNBT(provider, tag.getCompound("water"));
        }
        renderStack = tag.contains("item") ? ItemStack.parseOptional(provider, tag.getCompound("item")) : ItemStack.EMPTY;
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        operatingTicks = nbt.getInt("progress");
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putInt("progress", operatingTicks);
    }
}
