package mekwasher.client;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekwasher.tile.TileEntityElectricDeployer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism machine screen: held item (top) is applied onto the processed item (bottom) -> outputs. Standard Mekanism tabs.
 */
public class GuiElectricDeployer extends GuiConfigurableTile<TileEntityElectricDeployer, MekanismTileContainer<TileEntityElectricDeployer>> {

    public GuiElectricDeployer(MekanismTileContainer<TileEntityElectricDeployer> container, Inventory inv, Component title) {
        super(container, inv, title);
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        // Arrow from the held item down onto the processed item
        addRenderableWidget(new GuiDownArrow(this, 34, 39));
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tile::hasNotEnoughEnergy);
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
        addRenderableWidget(new GuiProgress(tile::getScaledProgress, ProgressType.SMALL_RIGHT, this, 54, 40))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tile::hasNoOutputSpace)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tile::hasNoRecipe);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
