package mekwasher.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Kept in its own class so dedicated servers never load client-only classes. */
public final class ClientLevelAccess {

    @Nullable
    public static Level getClientLevel() {
        return Minecraft.getInstance().level;
    }

    private ClientLevelAccess() {
    }
}
