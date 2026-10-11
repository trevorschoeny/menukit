package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.VanillaLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A setting whose value is an item: a slot showing the item the setting is about,
 * and a lens onto it. Made for settings pages, so an options screen
 * can let the player pick the items a setting is about (Inventory Plus's item lock
 * groups: which items are locked).
 *
 * <pre>{@code
 * ItemPicker.builder().at(0, y)
 *         .state(() -> config.lockedItem(), config::setLockedItem)
 *         .build();
 * }</pre>
 *
 * <h3>A setting value, never a player's item</h3>
 *
 * The value is an {@link Item} (the kind of item, no count, no components), held
 * by the consumer: {@code get} is read every frame, {@code set} receives a change.
 * Nothing here touches the player's inventory or the container behind a screen,
 * and nothing is sent to the server. The picker is client-only and is MenuKit, not
 * Containers: choosing an item never takes one, and the slot is not a real slot.
 * {@link Items#AIR} is "no item": the slot shows empty.
 *
 * <h3>Input</h3>
 *
 * <ul>
 *   <li><b>Left click</b> (or Enter or Space when focused) opens the item chooser
 *       over the current screen: every item in the game, a search field that
 *       narrows them by name or id, Clear and Cancel. Clicking an item hands it to
 *       {@code set} and returns to the screen the picker is on; Escape or Cancel
 *       returns with nothing changed; Clear hands it {@link Items#AIR}.</li>
 *   <li><b>Right click</b> empties it: {@code set} receives {@link Items#AIR}.</li>
 * </ul>
 *
 * Disabled (its own {@code disabledWhen}, or its panel's or container's), it draws
 * faded and takes nothing. Hovered, it shows the item's name, unless a
 * {@code tooltip} is given.
 *
 * <h3>The kind of screen</h3>
 *
 * It draws as a slot in the kind of screen it is on: MenuKit's recessed slot on a
 * container screen, vanilla's slot sprite on an options screen (the slot the
 * Superflat layer list draws its blocks in), with vanilla's slot highlight under
 * the mouse on both. The chooser it opens is the same kind as the screen it opens
 * over ({@link ScreenKind#of}), as a dialog would be.
 *
 * <p>18 x 18, vanilla's slot size; it never shrinks.
 */
public class ItemPicker extends AbstractPanelElement {

    /** A slot's size: 16 px of item and a 1 px frame each side. */
    public static final int SIZE = 18;

    // Vanilla's slot highlight (AbstractContainerScreen, 26.2): a 24 x 24 glow behind
    // the item and a sheen in front of it, both 4 px out from the item's corner.
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");

    private final Supplier<Item> stateGet;
    private final Consumer<Item> stateSet;
    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at the
    // first screen attach, as a Button's is.
    private @Nullable ElementWidget widget;

    protected ItemPicker(Builder b) {
        super(b);
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
    }

    /** Starts a picker. Give it a {@code state(get, set)}. */
    public static Builder builder() {
        return new Builder();
    }

    /** The item the picker shows right now ({@link Items#AIR} when empty). */
    public Item getCurrentItem() {
        Item item = stateGet.get();
        return item != null ? item : Items.AIR;
    }

    // Always one slot in size.

    @Override public void layoutWithin(int budget) {}
    @Override public void fillWidth(int width) {}
    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        boolean hovered = isHovered(ctx);
        Item item = getCurrentItem();
        if (widget != null) widget.track(sx, sy, SIZE, SIZE, hovered, !disabled, itemName(item));
        boolean highlighted = !disabled && (hovered || (widget != null && widget.focused()));
        drawSlot(ctx.graphics(), ctx.kind(), sx, sy, item, highlighted, disabled);
        if (hovered) queueTooltip(ctx);
    }

    /**
     * One slot holding {@code item}, as the picker and the chooser's grid both draw
     * it: the kind's slot, vanilla's highlight behind and in front of the item while
     * {@code highlighted}, the item drawn as a picture (a fake item: no entity, no
     * inventory behind it), faded while {@code disabled}.
     */
    static void drawSlot(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, Item item,
                         boolean highlighted, boolean disabled) {
        if (kind == ScreenKind.OPTIONS) VanillaLook.slot(g, x, y, disabled);
        else SlotRendering.drawSlotBackground(g, x, y, SIZE, disabled);
        int ix = x + SlotRendering.ITEM_INSET, iy = y + SlotRendering.ITEM_INSET;
        if (highlighted) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, ix - 4, iy - 4, 24, 24);
        if (item != Items.AIR) g.fakeItem(new ItemStack(item), ix, iy);
        if (highlighted) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, ix - 4, iy - 4, 24, 24);
        if (disabled && item != Items.AIR) g.fill(ix, iy, ix + 16, iy + 16, ElementConstants.DISABLED_OVERLAY);
    }

    /** The item's name as a player reads it, or "Empty" for none: the default tooltip and the narration. */
    static Component itemName(Item item) {
        return item == Items.AIR ? Component.literal("Empty") : new ItemStack(item).getHoverName();
    }

    /** Left click opens the chooser; right click empties the setting. */
    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        if (button == Click.RIGHT) {
            ElementWidget.playClickSound();
            stateSet.accept(Items.AIR);
            return true;
        }
        if (button != Click.LEFT) return false;
        ElementWidget.playClickSound();
        openChooser();
        return true;
    }

    /** Enter or Space on the focused picker (vanilla played the sound). */
    private void press() {
        if (!ownDisabled()) openChooser();
    }

    /** The chooser over the screen that is open now, the same kind as it; a pick goes to {@code set}. */
    private void openChooser() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ItemChooser(mc.gui.screen(), stateSet));
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget == null) widget = new ElementWidget(this::press);
        widget.attach(screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget != null) widget.detach(screen);
    }

    public static class Builder extends AbstractPanelElement.Builder<ItemPicker, Builder> {
        private @Nullable Supplier<Item> stateGet;
        private @Nullable Consumer<Item> stateSet;

        protected Builder() {
            this.width = SIZE;
            this.height = SIZE;
        }

        @Override protected Builder self() { return this; }

        /**
         * Required: the lens onto the setting. {@code get} is read every frame
         * ({@link Items#AIR}, or {@code null}, for none); {@code set} receives the
         * item the player picks, or {@link Items#AIR} when they empty it.
         */
        public Builder state(Supplier<Item> get, Consumer<Item> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public ItemPicker build() {
            require(stateGet != null, "state(get, set) is required; an ItemPicker shows the consumer's value");
            // Hovered, a picker names its item unless the consumer says something else.
            if (tooltip == null) {
                Supplier<Item> get = stateGet;
                tooltip = () -> {
                    Item item = get.get();
                    return item == null || item == Items.AIR ? null : itemName(item);
                };
            }
            return new ItemPicker(this);
        }
    }
}
