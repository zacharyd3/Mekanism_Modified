package mekwasher.registries;

import mekanism.common.block.attribute.AttributeParticleFX;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.lib.math.Pos3D;
import mekanism.common.lib.transmitter.TransmissionType;
import mekwasher.MWLang;
import mekwasher.WasherConfig;
import mekwasher.tile.TileEntityBulkWasher;
import net.minecraft.core.particles.ParticleTypes;

public final class MWBlockTypes {

    // Standard Mekanism machine: facing, active light, security, redstone control, comparator,
    // and Speed / Energy / Muffling upgrade support all come from Machine.
    public static final Machine<TileEntityBulkWasher> BULK_WASHER = Machine.MachineBuilder
          .createMachine(() -> MWTileEntityTypes.BULK_WASHER, MWLang.DESCRIPTION_BULK_WASHER)
          .withGui(() -> MWContainerTypes.BULK_WASHER)
          .withEnergyConfig(() -> WasherConfig.get(WasherConfig.ENERGY_USAGE), () -> WasherConfig.get(WasherConfig.ENERGY_STORAGE))
          .withSideConfig(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.ENERGY)
          // Splashes instead of the default smoke/redstone particles while running
          .with(new AttributeParticleFX()
                .add(ParticleTypes.SPLASH, rand -> new Pos3D(rand.nextFloat() * 0.6F - 0.3F, rand.nextFloat() * 6.0F / 16.0F, 0.52))
                .add(ParticleTypes.BUBBLE_POP, rand -> new Pos3D(rand.nextFloat() * 0.6F - 0.3F, rand.nextFloat() * 6.0F / 16.0F, 0.52)))
          .build();

    private MWBlockTypes() {
    }
}
