package mekwasher.registries;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekwasher.MekWasher;
import mekwasher.tile.TileEntityBulkWasher;
import mekwasher.tile.TileEntityElectricDeployer;

public final class MWContainerTypes {

    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(MekWasher.MOD_ID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityBulkWasher>> BULK_WASHER =
          CONTAINER_TYPES.register(MWBlocks.BULK_WASHER, TileEntityBulkWasher.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityElectricDeployer>> ELECTRIC_DEPLOYER =
          CONTAINER_TYPES.register(MWBlocks.ELECTRIC_DEPLOYER, TileEntityElectricDeployer.class);

    private MWContainerTypes() {
    }
}
