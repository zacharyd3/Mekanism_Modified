package mekwasher.registries;

import mekanism.common.capabilities.Capabilities;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import mekwasher.MekWasher;
import mekwasher.tile.TileEntityBulkWasher;
import mekwasher.tile.TileEntityElectricDeployer;

public final class MWTileEntityTypes {

    public static final TileEntityTypeDeferredRegister TILE_ENTITY_TYPES = new TileEntityTypeDeferredRegister(MekWasher.MOD_ID);

    // mekBuilder wires up Mekanism's item/energy/security capabilities (incl. FE) based on side config
    public static final TileEntityTypeRegistryObject<TileEntityBulkWasher> BULK_WASHER = TILE_ENTITY_TYPES
          .<TileEntityBulkWasher>mekBuilder(MWBlocks.BULK_WASHER, TileEntityBulkWasher::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityElectricDeployer> ELECTRIC_DEPLOYER = TILE_ENTITY_TYPES
          .<TileEntityElectricDeployer>mekBuilder(MWBlocks.ELECTRIC_DEPLOYER, TileEntityElectricDeployer::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    private MWTileEntityTypes() {
    }
}
