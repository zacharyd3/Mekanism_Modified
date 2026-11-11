package mekwasher.util;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.equipment.sandPaper.SandPaperItem;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Mirrors how Create's Deployer picks a recipe for "processed item + held item":
 * sandpaper polishing, then the next deploying step of a sequenced assembly, then deploying, then (automatable) item application.
 */
public final class DeployingRecipes {

    // Slot-validation caches, rebuilt whenever the recipe manager changes (/reload, joining a server)
    private static RecipeManager cachedManager;
    private static final Map<Item, Boolean> VALID_PROCESSED = new IdentityHashMap<>();
    private static final Map<Item, Boolean> VALID_HELD = new IdentityHashMap<>();

    /**
     * Finds the recipe the Deployer would use. For sequenced assembly this also primes Create's "next result", so call it
     * right before {@link com.simibubi.create.foundation.recipe.RecipeApplier#applyRecipeOn} like Create does.
     */
    @Nullable
    public static Recipe<?> find(Level level, ItemStack processed, ItemStack held) {
        if (processed.isEmpty() || held.isEmpty()) {
            return null;
        }
        ItemStack single = processed.copyWithCount(1);
        if (held.getItem() instanceof SandPaperItem) {
            Optional<RecipeHolder<SandPaperPolishingRecipe>> polishing = AllRecipeTypes.SANDPAPER_POLISHING
                  .<SingleRecipeInput, SandPaperPolishingRecipe>find(new SingleRecipeInput(single), level)
                  .filter(AllRecipeTypes.CAN_BE_AUTOMATED);
            if (polishing.isPresent()) {
                return polishing.get().value();
            }
        }
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, single);
        inv.setStackInSlot(1, held);
        RecipeWrapper wrapper = new RecipeWrapper(inv);
        Optional<RecipeHolder<DeployerApplicationRecipe>> sequenced = SequencedAssemblyRecipe.getRecipe(level, wrapper,
              AllRecipeTypes.DEPLOYING.<RecipeWrapper, DeployerApplicationRecipe>getType(), DeployerApplicationRecipe.class);
        if (sequenced.isPresent()) {
            return sequenced.get().value();
        }
        Optional<RecipeHolder<DeployerApplicationRecipe>> deploying = AllRecipeTypes.DEPLOYING
              .<RecipeWrapper, DeployerApplicationRecipe>find(wrapper, level).filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (deploying.isPresent()) {
            return deploying.get().value();
        }
        return AllRecipeTypes.ITEM_APPLICATION.<RecipeWrapper, ManualApplicationRecipe>find(wrapper, level)
              .filter(AllRecipeTypes.CAN_BE_AUTOMATED).map(RecipeHolder::value).orElse(null);
    }

    public static boolean keepsHeldItem(Recipe<?> recipe) {
        return recipe instanceof ItemApplicationRecipe application && application.shouldKeepHeldItem();
    }

    /** Could this item be the thing a deployer works on? */
    public static boolean isValidProcessed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = MachineUtil.findAnyLevel();
        if (level == null) {
            return true;
        }
        if (stack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            // Partially assembled item: valid if its next step is a deploying step
            return nextSequencedStep(level, stack) instanceof DeployerApplicationRecipe;
        }
        checkCache(level);
        return VALID_PROCESSED.computeIfAbsent(stack.getItem(), item -> computeValidProcessed(level, stack));
    }

    /** Could this item be held by a deployer for some recipe? */
    public static boolean isValidHeld(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SandPaperItem) {
            return true;
        }
        Level level = MachineUtil.findAnyLevel();
        if (level == null) {
            return true;
        }
        checkCache(level);
        return VALID_HELD.computeIfAbsent(stack.getItem(), item -> computeValidHeld(level, stack));
    }

    private static void checkCache(Level level) {
        RecipeManager manager = level.getRecipeManager();
        if (manager != cachedManager) {
            cachedManager = manager;
            VALID_PROCESSED.clear();
            VALID_HELD.clear();
        }
    }

    private static List<? extends ItemApplicationRecipe> applicationRecipes(Level level) {
        RecipeManager manager = level.getRecipeManager();
        List<ItemApplicationRecipe> all = new java.util.ArrayList<>();
        manager.getAllRecipesFor(AllRecipeTypes.DEPLOYING.<RecipeWrapper, DeployerApplicationRecipe>getType()).forEach(h -> all.add(h.value()));
        manager.getAllRecipesFor(AllRecipeTypes.ITEM_APPLICATION.<RecipeWrapper, ManualApplicationRecipe>getType()).stream()
              .filter(AllRecipeTypes.CAN_BE_AUTOMATED).forEach(h -> all.add(h.value()));
        for (RecipeHolder<SequencedAssemblyRecipe> holder : sequencedRecipes(level)) {
            for (SequencedRecipe<?> step : holder.value().getSequence()) {
                if (step.getRecipe() instanceof DeployerApplicationRecipe deploying) {
                    all.add(deploying);
                }
            }
        }
        return all;
    }

    private static List<RecipeHolder<SequencedAssemblyRecipe>> sequencedRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.SEQUENCED_ASSEMBLY.<RecipeWrapper, SequencedAssemblyRecipe>getType());
    }

    private static boolean computeValidProcessed(Level level, ItemStack stack) {
        for (ItemApplicationRecipe recipe : applicationRecipes(level)) {
            if (recipe.getProcessedItem().test(stack)) {
                return true;
            }
        }
        for (RecipeHolder<SequencedAssemblyRecipe> holder : sequencedRecipes(level)) {
            SequencedAssemblyRecipe recipe = holder.value();
            if (recipe.getIngredient().test(stack) && !recipe.getSequence().isEmpty()
                && recipe.getSequence().getFirst().getRecipe() instanceof DeployerApplicationRecipe) {
                return true;
            }
        }
        for (RecipeHolder<SandPaperPolishingRecipe> holder : level.getRecipeManager()
              .getAllRecipesFor(AllRecipeTypes.SANDPAPER_POLISHING.<SingleRecipeInput, SandPaperPolishingRecipe>getType())) {
            if (!holder.value().getIngredients().isEmpty() && holder.value().getIngredients().getFirst().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean computeValidHeld(Level level, ItemStack stack) {
        for (ItemApplicationRecipe recipe : applicationRecipes(level)) {
            if (recipe.getIngredients().size() > 1 && recipe.getRequiredHeldItem().test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ProcessingRecipe<?, ?> nextSequencedStep(Level level, ItemStack stack) {
        SequencedAssemblyRecipe.SequencedAssembly progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (progress == null) {
            return null;
        }
        for (RecipeHolder<SequencedAssemblyRecipe> holder : sequencedRecipes(level)) {
            if (holder.id().equals(progress.id())) {
                List<SequencedRecipe<?>> sequence = holder.value().getSequence();
                return sequence.isEmpty() ? null : sequence.get(progress.step() % sequence.size()).getRecipe();
            }
        }
        return null;
    }

    private DeployingRecipes() {
    }
}
