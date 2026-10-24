package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.panel.SlotItemLooks;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The declared {@link SlotItemLooks} (Fluorite), asked in id order by MenuKit's slot hook
 * ({@code MKSlotItemLookMixin}). Render thread, but declared from client init.
 */
@ApiStatus.Internal
public final class SlotItemLookRegistry {

    private SlotItemLookRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private record Declared(String id, SlotItemLooks.Look look) {}

    private static final List<Declared> LOOKS = new CopyOnWriteArrayList<>();

    /** {@code SlotItemLooks.declare}: adds a look in id order. */
    public static synchronized void declare(String id, SlotItemLooks.Look look) {
        for (Declared d : LOOKS) {
            if (d.id().equals(id)) throw new IllegalStateException("SlotItemLooks look '" + id + "' is already declared");
        }
        LOOKS.add(new Declared(id, look));
        LOOKS.sort(Comparator.comparing(Declared::id));
    }

    /**
     * The first look's frame for {@code slot}, or {@code null} for vanilla's draw. A look that
     * throws is logged once and skipped from then on.
     */
    public static ItemAnimation.@Nullable Frame frameFor(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack) {
        for (Declared d : LOOKS) {
            try {
                ItemAnimation.Frame f = d.look().look(screen, slot, stack);
                if (f != null) return f;
            } catch (RuntimeException e) {
                LOGGER.error("[MenuKit] Slot item look '{}' threw; skipping it from now on", d.id(), e);
                LOOKS.remove(d);
            }
        }
        return null;
    }
}
