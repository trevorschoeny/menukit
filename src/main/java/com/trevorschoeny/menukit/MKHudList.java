package com.trevorschoeny.menukit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * HUD dynamic list element — re-evaluates its data source each frame and
 * renders child elements for each item.
 *
 * <p>The template callback builds elements for each data item. Elements are
 * ephemeral — created and rendered in the same frame, then discarded.
 *
 * <p>Usage:
 * <pre>{@code
 * .list(0, 0,
 *     () -> player.getActiveEffects().stream().toList(),
 *     (effect, row) -> {
 *         row.text(() -> effect.getEffect().getDescription().getString());
 *         row.text(() -> formatDuration(effect.getDuration()));
 *     })
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudList<T> implements MKHudElement {

    /** Callback for building elements per data item. */
    @FunctionalInterface
    public interface TemplateCallback<T> {
        void build(T item, RowBuilder row);
    }

    private final int relX, relY;
    private final Supplier<List<T>> dataSource;
    private final TemplateCallback<T> template;
    private final MKHudGroup.Layout layout;
    private final int spacing;

    MKHudList(int relX, int relY, Supplier<List<T>> dataSource,
              TemplateCallback<T> template,
              MKHudGroup.Layout layout, int spacing) {
        this.relX = relX;
        this.relY = relY;
        this.dataSource = dataSource;
        this.template = template;
        this.layout = layout;
        this.spacing = spacing;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, DeltaTracker dt) {
        List<T> data = dataSource.get();
        if (data == null || data.isEmpty()) return;

        int cursorX = x + relX;
        int cursorY = y + relY;

        for (T item : data) {
            // Build elements for this row
            RowBuilder row = new RowBuilder();
            template.build(item, row);

            // Create a temporary group and render it
            MKHudGroup group = new MKHudGroup(0, 0,
                    MKHudGroup.Layout.ROW, 4,
                    List.copyOf(row.elements), null);
            group.render(graphics, cursorX, cursorY, dt);

            // Advance cursor
            if (layout == MKHudGroup.Layout.COLUMN) {
                cursorY += group.getHeight() + spacing;
            } else {
                cursorX += group.getWidth() + spacing;
            }
        }
    }

    @Override
    public int getWidth() {
        // Dynamic — can't know width without evaluating data
        // Return a reasonable estimate
        return relX + 100;
    }

    @Override
    public int getHeight() {
        // Dynamic — estimate based on current data size
        List<T> data = dataSource.get();
        if (data == null || data.isEmpty()) return relY;
        return relY + data.size() * (9 + spacing); // 9 = font height per row
    }

    /**
     * Builder for constructing elements within a list row.
     * Passed to the template callback.
     */
    public static class RowBuilder {
        final List<MKHudElement> elements = new ArrayList<>();

        /** Adds a text element to this row. */
        public RowBuilder text(Supplier<String> text) {
            elements.add(new MKHudText(0, 0,
                    () -> Component.literal(text.get()),
                    0xFFFFFFFF, true, 1.0f, false, null));
            return this;
        }

        /** Adds a text element with custom color. */
        public RowBuilder text(Supplier<String> text, int color) {
            elements.add(new MKHudText(0, 0,
                    () -> Component.literal(text.get()),
                    color, true, 1.0f, false, null));
            return this;
        }

        /** Adds an item icon to this row. */
        public RowBuilder item(Supplier<ItemStack> item) {
            elements.add(new MKHudItem(0, 0, item, 16, false, false, null));
            return this;
        }

        /** Adds a small (8×8) item icon to this row. */
        public RowBuilder itemSmall(Supplier<ItemStack> item) {
            elements.add(new MKHudItem(0, 0, item, 8, false, false, null));
            return this;
        }
    }
}
