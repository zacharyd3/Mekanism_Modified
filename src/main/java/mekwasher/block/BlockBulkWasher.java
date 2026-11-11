package mekwasher.block;

import mekanism.common.block.prefab.BlockTile.BlockTileModel;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.WorldUtils;
import mekanism.api.security.IBlockSecurityUtils;
import mekwasher.tile.TileEntityBulkWasher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism machine block that additionally lets players right-click with a water bucket (or any fluid container) to fill it.
 */
public class BlockBulkWasher extends BlockTileModel<TileEntityBulkWasher, Machine<TileEntityBulkWasher>> {

    public BlockBulkWasher(Machine<TileEntityBulkWasher> type, java.util.function.UnaryOperator<Properties> propertiesModifier) {
        super(type, propertiesModifier);
    }

    @NotNull
    @Override
    protected ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!stack.isEmpty() && !player.isShiftKeyDown() && Capabilities.FLUID.getCapability(stack) != null) {
            TileEntityBulkWasher tile = WorldUtils.getTileEntity(TileEntityBulkWasher.class, level, pos);
            if (tile != null) {
                if (level.isClientSide) {
                    return ItemInteractionResult.SUCCESS;
                }
                if (!IBlockSecurityUtils.INSTANCE.canAccessOrDisplayError(player, level, pos, tile)) {
                    return ItemInteractionResult.FAIL;
                }
                if (FluidUtils.handleTankInteraction(player, hand, stack, tile.waterTank)) {
                    player.getInventory().setChanged();
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
