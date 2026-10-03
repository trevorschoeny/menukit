package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.MKScreen;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The searchable item chooser an {@link ItemPicker} opens: every item in the game
 * in a scrolling grid, a search field over it, Clear and Cancel under it. Made of
 * MenuKit's own elements on a standalone screen, the same kind of screen as the one
 * it opens over ({@link ScreenKind#of}), so it reads like a dialog of that screen:
 * on an options screen the grid is vanilla's list band and slots, on a container
 * screen MenuKit's grey.
 *
 * <p>It only reads the item registry and draws pictures of items. It never touches
 * the player's inventory or a container, and sends nothing to the server: a pick is
 * a setting value handed to the picker's {@code set}.
 *
 * <p>Clicking an item (or Enter in the search field, for the first match) hands it
 * to {@code set} and returns to the screen it opened over; Clear hands
 * {@link Items#AIR}; Cancel and Escape return with nothing changed. Package-private:
 * consumers reach it through {@link ItemPicker}.
 */
final class ItemChooser extends MKScreen {

    /** Columns in the grid: a vanilla inventory row. */
    private static final int COLUMNS = 9;
    /** Rows on screen at once; the rest scroll. */
    private static final int ROWS = 6;
    private static final int GRID_WIDTH = COLUMNS * ItemPicker.SIZE;
    /** The scroll area's outer width: the grid plus the scrollbar lane. */
    private static final int WIDTH = GRID_WIDTH + ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
    private static final int FIELD_HEIGHT = 20;
    /** Room between rows of the layout; clears the 2 px separators an options screen draws around the list. */
    private static final int GAP = 6;
    private static final int BUTTON_WIDTH = (WIDTH - 4) / 2;

    private static final Component TITLE = Component.literal("Choose an item");

    /** One choosable item, with what a search matches against, lower-cased once. */
    private record Choice(Item item, String name, String id) {}

    /** The search field, focused when the chooser opens. */
    private final TextField search;

    /** A chooser over {@code parent} (the screen the picker is on); a pick goes to {@code set}. */
    ItemChooser(@Nullable Screen parent, Consumer<Item> set) {
        this(parent, set, new State());
    }

    // The view state is made first and handed to the panel, so the screen can hold the
    // search field it builds (a constructor cannot make it before calling super).
    private ItemChooser(@Nullable Screen parent, Consumer<Item> set, State state) {
        super(TITLE, ScreenKind.of(parent), List.of(main(parent, set, state)));
        this.search = state.field;
        // Escape (and Cancel) go back to the screen the picker is on, unchanged.
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    /** Typing goes straight to the search field, as the creative inventory's does. */
    @Override
    protected void init() {
        super.init();
        if (search.widget() != null) setFocused(search.widget());
    }

    // ── The view state ─────────────────────────────────────────────────

    /**
     * The chooser's own view state: what the player typed, how far the grid
     * scrolled, and the search's result, kept while the query stays the same.
     */
    private static final class State {
        String query = "";
        double scroll = 0;
        @Nullable String shownFor;
        List<Choice> shown = List.of();
        /** Set while the panel is built, so init can focus it. */
        TextField field;

        /** Every item but air, in the registry's order, with its name and id for searching. */
        final List<Choice> all = allChoices();

        /** The items matching the query: each word of it in the item's name or id. */
        List<Choice> shown() {
            if (!query.equals(shownFor)) {
                String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
                List<Choice> out = new ArrayList<>();
                for (Choice c : all) {
                    boolean match = true;
                    for (String w : words) {
                        if (!w.isEmpty() && !c.name().contains(w) && !c.id().contains(w)) {
                            match = false;
                            break;
                        }
                    }
                    if (match) out.add(c);
                }
                shown = out;
                shownFor = query;
            }
            return shown;
        }
    }

    private static List<Choice> allChoices() {
        List<Choice> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            out.add(new Choice(item,
                    new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT),
                    BuiltInRegistries.ITEM.getKey(item).toString()));
        }
        return out;
    }

    // ── The panel ──────────────────────────────────────────────────────

    /**
     * Search field, grid, then Clear and Cancel, in one column. On an options screen
     * the panel has no box (vanilla's backdrop shows, as on Options); on a container
     * screen it is MenuKit's grey panel.
     */
    private static Panel main(@Nullable Screen parent, Consumer<Item> set, State state) {
        Runnable back = () -> Minecraft.getInstance().gui.setScreen(parent);
        Consumer<Item> pick = item -> {
            set.accept(item);
            back.run();
        };

        state.field = TextField.builder().at(0, 0).size(WIDTH, FIELD_HEIGHT)
                .hint(Component.literal("Search items"))
                .state(() -> state.query, q -> {
                    state.query = q;
                    state.scroll = 0;   // a new search starts at the top of its results
                })
                // Enter takes the first match, so typing a name and Enter is a pick.
                .onSubmit(q -> {
                    List<Choice> shown = state.shown();
                    if (!shown.isEmpty()) pick.accept(shown.get(0).item());
                })
                .build();

        int gridTop = FIELD_HEIGHT + GAP;
        ScrollContainer grid = ScrollContainer.builder().at(0, gridTop).size(WIDTH, ROWS * ItemPicker.SIZE)
                .content(List.of(new Grid(state, pick)))
                .state(() -> state.scroll, v -> state.scroll = v)
                .build();

        int buttonsTop = gridTop + ROWS * ItemPicker.SIZE + GAP;
        Button clear = Button.builder().at(0, buttonsTop).size(BUTTON_WIDTH, 20)
                .label(Component.literal("Clear")).onClick(() -> pick.accept(Items.AIR)).build();
        Button cancel = Button.builder().at(WIDTH - BUTTON_WIDTH, buttonsTop).size(BUTTON_WIDTH, 20)
                .label(Component.literal("Cancel")).onClick(back).build();

        return Panel.builder("menukit:item_chooser")
                .style(ScreenKind.of(parent) == ScreenKind.OPTIONS ? PanelStyle.NONE : PanelStyle.RAISED)
                .position(PanelPosition.main())
                .add(state.field).add(grid).add(clear).add(cancel)
                .build();
    }

    // ── The grid ───────────────────────────────────────────────────────

    /**
     * The matching items, nine to a row, as slots in the kind of screen. As tall as
     * its rows, read every frame, so the scroll area around it follows the search.
     * Draws only the rows on screen; the scroll area clips the rest.
     */
    private static final class Grid extends AbstractPanelElement {
        private final State state;
        private final Consumer<Item> pick;

        Grid(State state, Consumer<Item> pick) {
            this.state = state;
            this.pick = pick;
            this.width = GRID_WIDTH;
        }

        private int rows() {
            return (state.shown().size() + COLUMNS - 1) / COLUMNS;
        }

        @Override public int getHeight() { return Math.max(1, rows()) * ItemPicker.SIZE; }
        @Override public void layoutWithin(int budget) {}
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        /** The item under a screen point, or {@code null} (a gap after the last item, or outside). */
        private @Nullable Item itemAt(int originX, int originY, double mouseX, double mouseY) {
            double lx = mouseX - (originX + childX), ly = mouseY - (originY + childY);
            if (lx < 0 || ly < 0 || lx >= GRID_WIDTH) return null;
            int index = (int) (ly / ItemPicker.SIZE) * COLUMNS + (int) (lx / ItemPicker.SIZE);
            List<Choice> shown = state.shown();
            return index < shown.size() ? shown.get(index).item() : null;
        }

        @Override
        public void render(RenderContext ctx) {
            var g = ctx.graphics();
            int gx = ctx.originX() + childX, gy = ctx.originY() + childY;
            List<Choice> shown = state.shown();
            if (shown.isEmpty()) {
                g.text(Minecraft.getInstance().font, "No items match", gx + 2, gy + 5,
                        ctx.kind().textColor(TextRole.MUTED), ctx.kind().textShadow());
                return;
            }
            // Hover only where the grid shows: the scroll area clips it, and a cell
            // scrolled out of view must not light up under the search field.
            Item hovered = ctx.hasMouseInput() && g.containsPointInScissor(ctx.mouseX(), ctx.mouseY())
                    ? itemAt(ctx.originX(), ctx.originY(), ctx.mouseX(), ctx.mouseY()) : null;
            // Only the rows on screen: the full list is over a thousand items.
            int first = Math.max(0, -gy / ItemPicker.SIZE);
            int last = Math.min(rows() - 1, (g.guiHeight() - gy) / ItemPicker.SIZE);
            for (int row = first; row <= last; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int i = row * COLUMNS + col;
                    if (i >= shown.size()) break;
                    Item item = shown.get(i).item();
                    ItemPicker.drawSlot(g, ctx.kind(), gx + col * ItemPicker.SIZE, gy + row * ItemPicker.SIZE,
                            item, item == hovered, false);
                }
            }
            if (hovered != null) MKTooltip.queue(g, ItemPicker.itemName(hovered), ctx.mouseX(), ctx.mouseY());
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            Item item = itemAt(in.originX(), in.originY(), in.mouseX(), in.mouseY());
            if (item == null) return false;
            ElementWidget.playClickSound();
            pick.accept(item);
            return true;
        }
    }
}
