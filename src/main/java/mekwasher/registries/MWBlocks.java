package mekwasher.registries;

import java.util.EnumMap;
import java.util.Map;
import mekanism.api.RelativeSide;
import mekanism.common.attachments.component.AttachedEjector;
import mekanism.common.attachments.component.AttachedSideConfig;
import mekanism.common.attachments.component.AttachedSideConfig.LightConfigInfo;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.containers.fluid.FluidTanksBuilder;
import mekanism.common.attachments.containers.item.ItemSlotsBuilder;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.resource.BlockResourceInfo;
import mekanism.common.tile.component.config.DataType;
import mekwasher.MekWasher;
import mekwasher.WasherConfig;
import mekwasher.block.BlockBulkWasher;
import mekwasher.tile.TileEntityBulkWasher;
import net.minecraft.Util;

public final class MWBlocks {

    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekWasher.MOD_ID);

    /** Default side config: standard machine item layout, energy in on all sides, water piped in through the top. */
    public static final AttachedSideConfig BULK_WASHER_SIDE_CONFIG = Util.make(() -> {
        Map<TransmissionType, LightConfigInfo> configInfo = new EnumMap<>(TransmissionType.class);
        configInfo.put(TransmissionType.ITEM, LightConfigInfo.MACHINE);
        configInfo.put(TransmissionType.FLUID, new LightConfigInfo(Map.of(RelativeSide.TOP, DataType.INPUT), false));
        configInfo.put(TransmissionType.ENERGY, LightConfigInfo.INPUT_ONLY);
        return new AttachedSideConfig(configInfo);
    });

    public static final BlockRegistryObject<BlockBulkWasher, ItemBlockTooltip<BlockBulkWasher>> BULK_WASHER =
          BLOCKS.<BlockBulkWasher, ItemBlockTooltip<BlockBulkWasher>>register(
                "bulk_washer",
                () -> new BlockBulkWasher(MWBlockTypes.BULK_WASHER, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor())),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                      .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                      .component(MekanismDataComponents.SIDE_CONFIG, BULK_WASHER_SIDE_CONFIG))
          ).forItemHolder(holder -> holder
                // Must match the slot/tank order in TileEntityBulkWasher so contents survive being picked up
                .addAttachmentOnlyContainers(ContainerType.FLUID, () -> FluidTanksBuilder.builder()
                      .addBasic(() -> WasherConfig.get(WasherConfig.WATER_CAPACITY), TileEntityBulkWasher::isWater)
                      .build())
                .addAttachmentOnlyContainers(ContainerType.ITEM, () -> ItemSlotsBuilder.builder()
                      .addInput(TileEntityBulkWasher::isWashable)
                      .addOutput(TileEntityBulkWasher.OUTPUT_SLOTS)
                      .addFluidFillSlot(0)
                      .addOutput()
                      .addEnergy()
                      .build()));

    private MWBlocks() {
    }
}
