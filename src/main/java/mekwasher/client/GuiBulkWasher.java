package mekwasher.client;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekwasher.tile.TileEntityBulkWasher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism-style screen laid out like the Nutritional Liquifier: item input -> progress -> outputs, water gauge with a
 * bucket fill slot, horizontal power bar, plus the standard side config / transporter / redstone / security / upgrade tabs.
 */
public class GuiBulkWasher extends GuiConfigurableTile<TileEntityBulkWasher, MekanismTileContainer<TileEntityBulkWasher>> {

    private GuiElement energyBar;

    public GuiBulkWasher(MekanismTileContainer<TileEntityBulkWasher> container, Inventory inv, Component title) {
        super(container, inv, title);
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        energyBar = addRenderableWidget(new GuiHorizontalPowerBar(this, tile.getEnergyContainer(), 115, 75))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tile::hasNotEnoughEnergy);
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
        addRenderableWidget(new GuiFluidGauge(() -> tile.waterTank, () -> tile.getFluidTanks(null), GaugeType.STANDARD, this, 133, 13))
              .warning(WarningType.NO_MATCHING_RECIPE, tile::hasNotEnoughWater);
        addRenderableWidget(new GuiDownArrow(this, 159, 44));
        addRenderableWidget(new GuiProgress(tile::getScaledProgress, ProgressType.SMALL_RIGHT, this, 50, 40))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tile::hasNoOutputSpace);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        renderInventoryText(guiGraphics, energyBar.getRelativeX());
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
