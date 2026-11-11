package mekwasher;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (world/serverconfig/mekwasher-server.toml).
 * Energy values use Mekanism's internal energy unit, exactly like Mekanism's own machine configs.
 */
public final class WasherConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.LongValue ENERGY_USAGE;
    public static final ModConfigSpec.LongValue ENERGY_STORAGE;
    public static final ModConfigSpec.IntValue TICKS_PER_OPERATION;
    public static final ModConfigSpec.IntValue WATER_CAPACITY;
    public static final ModConfigSpec.IntValue WATER_PER_OPERATION;
    public static final ModConfigSpec.LongValue DEPLOYER_ENERGY_USAGE;
    public static final ModConfigSpec.LongValue DEPLOYER_ENERGY_STORAGE;
    public static final ModConfigSpec.IntValue DEPLOYER_TICKS_PER_OPERATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Bulk Washer settings").push("bulk_washer");
        ENERGY_USAGE = builder.comment("Base energy used per tick while washing (before Speed/Energy upgrades). Mekanism's Crusher uses 50.")
              .defineInRange("energyUsage", 50L, 1L, Long.MAX_VALUE);
        ENERGY_STORAGE = builder.comment("Base energy buffer (before Energy upgrades). Mekanism's Crusher uses 20000.")
              .defineInRange("energyStorage", 20_000L, 1L, Long.MAX_VALUE);
        TICKS_PER_OPERATION = builder.comment("Base ticks to wash one item (before Speed upgrades). Mekanism's Crusher uses 200.")
              .defineInRange("ticksPerOperation", 100, 1, 72_000);
        WATER_CAPACITY = builder.comment("Water tank size in mB. Only affects machines placed/loaded after the change.")
              .defineInRange("waterCapacity", 10_000, 1_000, 1_000_000);
        WATER_PER_OPERATION = builder.comment("mB of water used for each item washed. Set to 0 to only require water to be present (no consumption).")
              .defineInRange("waterPerOperation", 50, 0, 1_000_000);
        builder.pop();

        builder.comment("Electric Deployer settings").push("electric_deployer");
        DEPLOYER_ENERGY_USAGE = builder.comment("Base energy used per tick while deploying (before Speed/Energy upgrades).")
              .defineInRange("energyUsage", 50L, 1L, Long.MAX_VALUE);
        DEPLOYER_ENERGY_STORAGE = builder.comment("Base energy buffer (before Energy upgrades).")
              .defineInRange("energyStorage", 20_000L, 1L, Long.MAX_VALUE);
        DEPLOYER_TICKS_PER_OPERATION = builder.comment("Base ticks per deploy (before Speed upgrades).")
              .defineInRange("ticksPerOperation", 40, 1, 72_000);
        builder.pop();
        SPEC = builder.build();
    }

    /** Reads a config value, falling back to its default if the server config is not loaded yet (e.g. main menu tooltips). */
    public static long get(ModConfigSpec.LongValue value) {
        return SPEC.isLoaded() ? value.getAsLong() : value.getDefault();
    }

    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.getAsInt() : value.getDefault();
    }

    private WasherConfig() {
    }
}
