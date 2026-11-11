package mekwasher.util;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

public final class MachineUtil {

    /**
     * A level to look recipes up in when validating slots (which happens without a tile level, e.g. for item attachments).
     */
    @Nullable
    public static Level findAnyLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.overworld();
        }
        if (FMLEnvironment.dist.isClient()) {
            return mekwasher.client.ClientLevelAccess.getClientLevel();
        }
        return null;
    }

    /** Whether all the given stacks fit into the output slots together (simulated, nothing is changed). */
    public static boolean fitsAll(List<OutputInventorySlot> outputs, List<ItemStack> stacks) {
        List<ItemStack> simulated = new ArrayList<>(outputs.size());
        for (OutputInventorySlot slot : outputs) {
            simulated.add(slot.getStack().copy());
        }
        for (ItemStack stack : stacks) {
            ItemStack toInsert = stack.copy();
            for (int i = 0; i < simulated.size() && !toInsert.isEmpty(); i++) {
                ItemStack existing = simulated.get(i);
                int limit = Math.min(toInsert.getMaxStackSize(), outputs.get(i).getLimit(toInsert));
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

    /** Inserts stacks into the outputs. Call {@link #fitsAll} first; returns anything that didn't fit. */
    public static List<ItemStack> insertAll(List<OutputInventorySlot> outputs, List<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack remainder = stack;
            for (OutputInventorySlot slot : outputs) {
                remainder = slot.insertItem(remainder, Action.EXECUTE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    break;
                }
            }
            if (!remainder.isEmpty()) {
                leftovers.add(remainder);
            }
        }
        return leftovers;
    }

    private MachineUtil() {
    }
}
