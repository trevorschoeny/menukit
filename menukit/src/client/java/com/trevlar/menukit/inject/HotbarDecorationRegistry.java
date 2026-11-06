package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.hud.HotbarDecorations;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The declared {@link HotbarDecorations} (8.0.0), drawn by {@code MKHotbarDecorationsMixin}
 * inside vanilla's hotbar. Render thread, but declared from client init.
 */
@ApiStatus.Internal
public final class HotbarDecorationRegistry {

    private HotbarDecorationRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    /** Nine slots in the hotbar, 20 px apart. */
    public static final int SLOTS = 9;

    private record Under(String id, HotbarDecorations.Decoration decoration) {}
    private record Hide(String id, HotbarDecorations.HideRule rule) {}

    private static final List<Under> UNDER = new CopyOnWriteArrayList<>();
    private static final List<Hide> HIDE = new CopyOnWriteArrayList<>();

    public static synchronized void under(String id, HotbarDecorations.Decoration decoration) {
        requireNew(id);
        UNDER.add(new Under(id, decoration));
        UNDER.sort(Comparator.comparing(Under::id));
    }

    public static synchronized void hideItem(String id, HotbarDecorations.HideRule rule) {
        requireNew(id);
        HIDE.add(new Hide(id, rule));
        HIDE.sort(Comparator.comparing(Hide::id));
    }

    // One id space for both kinds, as SlotDecorations has for under and over.
    private static void requireNew(String id) {
        if (UNDER.stream().anyMatch(u -> u.id().equals(id)) || HIDE.stream().anyMatch(h -> h.id().equals(id)))
            throw new IllegalStateException("HotbarDecorations '" + id + "' is already declared");
    }

    /** The left edge of hotbar slot {@code slot}'s item box, as vanilla places it. */
    public static int itemX(int screenW, int slot) {
        return screenW / 2 - 90 + slot * 20 + 2;
    }

    /** The top edge of a hotbar item box, as vanilla places it. */
    public static int itemY(int screenH) {
        return screenH - 19;
    }

    /** Every slot's under decorations, slot 0 to 8, each slot's in id order. */
    public static void drawUnder(GuiGraphicsExtractor graphics, Inventory inventory, int screenW, int screenH) {
        if (UNDER.isEmpty()) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            for (Under u : UNDER) {
                try {
                    u.decoration().draw(graphics, slot, stack, itemX(screenW, slot), itemY(screenH));
                } catch (RuntimeException e) {
                    if (UNDER.remove(u)) LOGGER.error("[MenuKit] Hotbar decoration '{}' threw; skipping it from now on", u.id(), e);
                }
            }
        }
    }

    /** Whether any rule hides {@code slot}'s item this frame. */
    public static boolean hidden(int slot, ItemStack stack) {
        for (Hide h : HIDE) {
            try {
                if (h.rule().hides(slot, stack)) return true;
            } catch (RuntimeException e) {
                if (HIDE.remove(h)) LOGGER.error("[MenuKit] Hotbar hide rule '{}' threw; skipping it from now on", h.id(), e);
            }
        }
        return false;
    }
}
