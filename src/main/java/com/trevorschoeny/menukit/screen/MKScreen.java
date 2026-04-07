package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.panel.MKPanel;
import com.trevorschoeny.menukit.panel.MKPanelDef;
import com.trevorschoeny.menukit.widget.MKButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Generic container screen for standalone MenuKit screens.
 *
 * <p>Extends {@link AbstractContainerScreen} for full slot support (click handling,
 * drag, shift-click, sync) but renders a <b>transparent/blurred background</b> by
 * default — just like vanilla's config screens. Panels render their own backgrounds
 * on top of the transparency.
 *
 * <p>The rendering pipeline:
 * <ol>
 *   <li>{@code renderBackground()} — vanilla draws the blurred/darkened world</li>
 *   <li>{@code renderBg()} — we render panel backgrounds + slot backgrounds (no full-screen panel)</li>
 *   <li>Vanilla renders slot items, hover highlights, widgets (buttons)</li>
 *   <li>{@code renderLabels()} — title + inventory label</li>
 * </ol>
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class MKScreen extends AbstractContainerScreen<MKMenu> {

    private final MKPanelDef panelDef;

    public MKScreen(MKMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.panelDef = MenuKit.getPanelDef(menu.getPanelName());

        // Set screen dimensions — use menu's calculated height
        if (panelDef != null) {
            this.imageWidth = panelDef.screenWidth() > 0
                    ? panelDef.screenWidth() : 176;
            this.imageHeight = menu.getScreenHeight();
        }

        // Position the "Inventory" label above the player inventory area
        // (hidden if no player inventory)
        if (menu.getPlayerInvY() >= 0) {
            this.inventoryLabelY = menu.getPlayerInvY() - 11;
        } else {
            this.inventoryLabelY = -999; // off-screen, won't render
        }
    }

    @Override
    protected void init() {
        super.init();
        if (panelDef == null) return;

        // Create buttons from the panel definition
        for (MKButton btn : MenuKit.createButtonsForStandaloneScreen(
                panelDef, leftPos, topPos)) {
            this.addRenderableWidget(btn);
        }
    }

    /**
     * Renders panel backgrounds and slot backgrounds on top of the transparent
     * world overlay. Does NOT render a full-screen panel — the vanilla
     * {@code renderBackground()} already provides the blurred/darkened world.
     *
     * <p>Each panel renders its own background (RAISED, INSET, FLAT, or NONE).
     * Slot backgrounds are rendered for all active slots (panel + player inventory).
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float delta,
                             int mouseX, int mouseY) {
        if (panelDef == null) return;

        // Render the screen background panel (full screen dimensions).
        // For RAISED/INSET/FLAT: draws the panel as the entire screen background (like a chest).
        // For NONE: draws nothing — the transparent/blurred world shows through.
        if (panelDef.style() != MKPanel.Style.NONE) {
            MKPanel.renderPanel(graphics, leftPos, topPos,
                    imageWidth, imageHeight, panelDef.style(), panelDef.customSprite());
        }

        // Render slot backgrounds for ALL active slots (panel + player inventory)
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                MKPanel.renderSlotBackground(graphics,
                        leftPos + slot.x - 1,
                        topPos + slot.y - 1);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Text color: dark on raised panels, white on transparent backgrounds
        int textColor = (panelDef != null && panelDef.style() != MKPanel.Style.NONE)
                ? 0x404040 : 0xFFFFFF;

        // Render the title centered at the top
        if (panelDef != null && panelDef.title() != null) {
            int titleWidth = this.font.width(this.title);
            graphics.drawString(this.font, this.title,
                    (imageWidth - titleWidth) / 2, 6, textColor, false);
        }

        // Render "Inventory" label above the player inventory (if present)
        if (menu.getPlayerInvY() >= 0) {
            graphics.drawString(this.font, this.playerInventoryTitle,
                    this.inventoryLabelX, this.inventoryLabelY, textColor, false);
        }
    }
}
