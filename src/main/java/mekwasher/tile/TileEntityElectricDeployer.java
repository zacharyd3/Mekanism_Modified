package mekwasher.tile;

import com.simibubi.create.foundation.recipe.RecipeApplier;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UpgradeUtils;
import mekwasher.WasherConfig;
import mekwasher.registries.MWBlocks;
import mekwasher.util.DeployingRecipes;
import mekwasher.util.MachineUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single-block Mekanism machine that performs Create's Deployer item processing: applies the held item to the processed item
 * (create:deploying, automatable create:item_application, sandpaper polishing and deploying steps of sequenced assembly).
 */
public class TileEntityElectricDeployer extends TileEntityConfigurableMachine {

    public static final int OUTPUT_SLOTS = 4;

    // Assigned from inside the super constructor (getInitial* methods) - must NOT have field initializers.
    private MachineEnergyContainer<TileEntityElectricDeployer> energyContainer;
    private InputInventorySlot inputSlot;
    private InputInventorySlot heldSlot;
    private List<OutputInventorySlot> outputSlots;
    private EnergyInventorySlot energySlot;

    private int operatingTicks;
    private int baseTicksRequired;
    public int ticksRequired;
    private int operationsPerTick;
    private boolean noOutputSpace;
    private boolean notEnoughEnergy;
    private boolean noRecipe;

    // Client render state, synced via the update tag
    private ItemStack renderInput = ItemStack.EMPTY;
    private ItemStack renderHeld = ItemStack.EMPTY;

    // Cheap "does a recipe exist" cache; the actual recipe is looked up fresh right before applying (needed for sequenced assembly)
    private ItemStack cachedInput = ItemStack.EMPTY;
    private ItemStack cachedHeld = ItemStack.EMPTY;
    private boolean cachedHasRecipe;

    public TileEntityElectricDeployer(BlockPos pos, BlockState state) {
        super(MWBlocks.ELECTRIC_DEPLOYER, pos, state);
        baseTicksRequired = WasherConfig.get(WasherConfig.DEPLOYER_TICKS_PER_OPERATION);
        ticksRequired = baseTicksRequired;
        operationsPerTick = 1;

        // Like Mekanism's advanced machines: input, output, and the held item as the "extra" slot so it can be fed from its own side
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            List<IInventorySlot> outputs = new ArrayList<>(outputSlots);
            List<IInventorySlot> inputAndOutputs = new ArrayList<>();
            inputAndOutputs.add(inputSlot);
            inputAndOutputs.addAll(outputSlots);
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputs));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputAndOutputs));
            itemConfig.addSlotInfo(DataType.EXTRA, new InventorySlotInfo(true, true, heldSlot));
            itemConfig.addSlotInfo(DataType.ENERGY, new InventorySlotInfo(true, true, energySlot));
        }
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
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
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(DeployingRecipes::isValidProcessed, listener, 30, 50))
              .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, () -> noRecipe));
        builder.addSlot(heldSlot = InputInventorySlot.at(DeployingRecipes::isValidHeld, listener, 30, 20))
              .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, () -> noRecipe));
        outputSlots = new ArrayList<>(OUTPUT_SLOTS);
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            OutputInventorySlot outputSlot = OutputInventorySlot.at(listener, 90 + (i % 2) * 18, 26 + (i / 2) * 18);
            builder.addSlot(outputSlot).tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, () -> noOutputSpace));
            outputSlots.add(outputSlot);
        }
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 143, 35));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        energySlot.fillContainerOrConvert();

        boolean active = false;
        boolean blockedOnSpace = false;
        boolean blockedOnEnergy = false;
        ItemStack input = inputSlot.getStack();
        ItemStack held = heldSlot.getStack();
        boolean hasRecipe = hasRecipe(input, held);
        if (!hasRecipe) {
            if (operatingTicks != 0) {
                operatingTicks = 0;
                markForSave();
            }
        } else if (operatingTicks >= ticksRequired) {
            // Finished but couldn't output last time; retry without using more energy
            blockedOnSpace = !deployAll();
        } else if (canFunction()) {
            long energyPerTick = energyContainer.getEnergyPerTick();
            if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL) < energyPerTick) {
                blockedOnEnergy = true;
            } else {
                energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                active = true;
                operatingTicks++;
                if (operatingTicks >= ticksRequired) {
                    blockedOnSpace = !deployAll();
                }
                markForSave();
            }
        }
        noRecipe = !input.isEmpty() && !held.isEmpty() && !hasRecipe;
        noOutputSpace = blockedOnSpace;
        notEnoughEnergy = blockedOnEnergy;
        setActive(active);

        ItemStack newInput = input.isEmpty() ? ItemStack.EMPTY : input.copyWithCount(1);
        ItemStack newHeld = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        if (!ItemStack.isSameItemSameComponents(newInput, renderInput) || !ItemStack.isSameItemSameComponents(newHeld, renderHeld)) {
            renderInput = newInput;
            renderHeld = newHeld;
            sendUpdatePacket = true;
        }
        return sendUpdatePacket;
    }

    /** Runs up to operationsPerTick deploys. Returns false if blocked on output space. */
    private boolean deployAll() {
        for (int i = 0; i < operationsPerTick; i++) {
            DeployResult result = deployOnce();
            if (result == DeployResult.NO_SPACE) {
                return false;
            } else if (result == DeployResult.NOTHING) {
                break;
            }
        }
        operatingTicks = 0;
        markForSave();
        return true;
    }

    private enum DeployResult {DONE, NO_SPACE, NOTHING}

    private DeployResult deployOnce() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return DeployResult.NOTHING;
        }
        ItemStack input = inputSlot.getStack();
        ItemStack held = heldSlot.getStack();
        // Looked up right before applying, exactly like Create's deployer (primes sequenced assembly's next result)
        Recipe<?> recipe = DeployingRecipes.find(serverLevel, input, held);
        if (recipe == null) {
            return DeployResult.NOTHING;
        }
        List<ItemStack> outputs = new ArrayList<>(RecipeApplier.applyRecipeOn(serverLevel, input.copyWithCount(1), recipe, true));

        // Work out what happens to the held item (same rules as Create's BeltDeployerCallbacks)
        ItemStack newHeld = held.copy();
        if (!DeployingRecipes.keepsHeldItem(recipe)) {
            if (newHeld.isDamageableItem()) {
                newHeld.hurtAndBreak(1, serverLevel, null, item -> {});
            } else {
                ItemStack remainder = newHeld.getCraftingRemainingItem();
                newHeld.shrink(1);
                if (!remainder.isEmpty()) {
                    outputs.add(remainder);
                }
            }
        }
        if (!MachineUtil.fitsAll(outputSlots, outputs)) {
            return DeployResult.NO_SPACE;
        }
        MachineUtil.insertAll(outputSlots, outputs);
        inputSlot.extractItem(1, Action.EXECUTE, AutomationType.INTERNAL);
        heldSlot.setStack(newHeld.isEmpty() ? ItemStack.EMPTY : newHeld);
        return DeployResult.DONE;
    }

    private boolean hasRecipe(ItemStack input, ItemStack held) {
        if (input.isEmpty() || held.isEmpty() || level == null) {
            return false;
        }
        if (!ItemStack.isSameItemSameComponents(input, cachedInput) || !ItemStack.isSameItemSameComponents(held, cachedHeld)) {
            cachedInput = input.copyWithCount(1);
            cachedHeld = held.copyWithCount(1);
            cachedHasRecipe = DeployingRecipes.find(level, cachedInput, cachedHeld) != null;
        }
        return cachedHasRecipe;
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
        return ticksRequired <= 0 ? 0 : Math.min(1, (double) operatingTicks / ticksRequired);
    }

    public boolean hasNoOutputSpace() {
        return noOutputSpace;
    }

    public boolean hasNotEnoughEnergy() {
        return notEnoughEnergy;
    }

    public boolean hasNoRecipe() {
        return noRecipe;
    }

    public ItemStack getRenderInput() {
        return renderInput;
    }

    public ItemStack getRenderHeld() {
        return renderHeld;
    }

    public MachineEnergyContainer<TileEntityElectricDeployer> getEnergyContainer() {
        return energyContainer;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> operatingTicks, value -> operatingTicks = value));
        container.track(SyncableInt.create(() -> ticksRequired, value -> ticksRequired = value));
        container.track(SyncableBoolean.create(() -> noOutputSpace, value -> noOutputSpace = value));
        container.track(SyncableBoolean.create(() -> notEnoughEnergy, value -> notEnoughEnergy = value));
        container.track(SyncableBoolean.create(() -> noRecipe, value -> noRecipe = value));
    }

    @NotNull
    @Override
    public CompoundTag getReducedUpdateTag(@NotNull HolderLookup.Provider provider) {
        CompoundTag updateTag = super.getReducedUpdateTag(provider);
        if (!renderInput.isEmpty()) {
            updateTag.put("input", renderInput.save(provider));
        }
        if (!renderHeld.isEmpty()) {
            updateTag.put("held", renderHeld.save(provider));
        }
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        renderInput = tag.contains("input") ? ItemStack.parseOptional(provider, tag.getCompound("input")) : ItemStack.EMPTY;
        renderHeld = tag.contains("held") ? ItemStack.parseOptional(provider, tag.getCompound("held")) : ItemStack.EMPTY;
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
