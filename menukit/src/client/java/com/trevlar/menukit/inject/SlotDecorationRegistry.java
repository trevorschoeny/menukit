package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The declared {@link SlotDecorations} (7.5.0), drawn by MenuKit's slot hooks: under the item
 * from {@code MKSlotDrawMixin}, over it from {@link ContainerScreenLayers#afterSlot}, inside
 * the slot's clip. Render thread, but declared from client init.
 */
@ApiStatus.Internal
public final class SlotDecorationRegistry {

    private SlotDecorationRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private record Declared(String id, boolean under, SlotDecorations.Decoration decoration) {}

    /** Every decoration, under ones then over ones, each in id order: one list, so a tooltip is asked in one walk. */
    private static final List<Declared> DECORATIONS = new CopyOnWriteArrayList<>();

    /** {@code SlotDecorations.under/over}: adds a decoration in id order. */
    public static synchronized void declare(String id, boolean under, SlotDecorations.Decoration decoration) {
        for (Declared d : DECORATIONS) {
            if (d.id().equals(id)) throw new IllegalStateException("SlotDecorations decoration '" + id + "' is already declared");
        }
        DECORATIONS.add(new Declared(id, under, decoration));
        // Under before over, each in id order: the tooltip walk's order.
        DECORATIONS.sort(Comparator.comparing((Declared d) -> !d.under()).thenComparing(Declared::id));
    }

    /** Draws one layer's decorations on {@code slot}, its item box at {@code (x, y)}. */
    public static void draw(boolean under, GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                            Slot slot, ItemStack stack, int x, int y) {
        for (Declared d : DECORATIONS) {
            if (d.under() != under) continue;
            try {
                d.decoration().draw(graphics, screen, slot, stack, x, y);
            } catch (RuntimeException e) {
                skip(d, e);
            }
        }
    }

    /**
     * On the hovered slot, at the end of its draw: the first decoration's tooltip at the mouse,
     * queued for this frame. Queued before vanilla's item tooltip, which then leaves it be
     * (the first tooltip queued in a frame is the one shown).
     */
    public static void tooltip(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot,
                               ItemStack stack, int mouseX, int mouseY) {
        if (DECORATIONS.isEmpty() || ((AbstractContainerScreenAccessor) screen).mk$getHoveredSlot() != slot) return;
        // The item box's top left on screen, wherever the slot is drawn (vanilla's pass, a
        // panel, a scrolled list): through the pose it draws with.
        Vector2f origin = graphics.pose().transformPosition(slot.x, slot.y, new Vector2f());
        int localX = (int) Math.floor(mouseX - origin.x), localY = (int) Math.floor(mouseY - origin.y);
        for (Declared d : DECORATIONS) {
            try {
                List<Component> lines = d.decoration().tooltip(screen, slot, stack, localX, localY);
                if (lines != null && !lines.isEmpty()) {
                    graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mouseX, mouseY);
                    return;
                }
            } catch (RuntimeException e) {
                skip(d, e);
            }
        }
    }

    /** A decoration that threw: logged once, never asked again. */
    private static void skip(Declared d, RuntimeException e) {
        if (DECORATIONS.remove(d)) LOGGER.error("[MenuKit] Slot decoration '{}' threw; skipping it from now on", d.id(), e);
    }
}
