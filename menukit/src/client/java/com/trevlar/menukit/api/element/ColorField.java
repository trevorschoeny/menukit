package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A colour setting: a swatch showing the colour, and a lens onto it. Clicking the
 * swatch (or Enter on it) opens a modal dialog over the screen with two tabs:
 * <ul>
 *   <li><b>Basic</b>, the default: Minecraft's sixteen dye colours in rainbow order,
 *       rows of six with the four neutrals last, each shown by its dye item, named in a
 *       tooltip (vanilla's colour name, "Light Blue"), the current one outlined.
 *       Clicking one sets the colour.</li>
 *   <li><b>Custom</b>: a saturation and brightness square, a hue bar, a transparency
 *       bar over a checkerboard (with {@code alpha(true)}), the colour before and now
 *       side by side, and a hex field.</li>
 * </ul>
 * Changes are written as they are made, so the screen behind shows them at once.
 * Done keeps them; Cancel and Escape write back the colour from before the dialog
 * opened.
 *
 * <pre>{@code
 * ColorField.builder()
 *         .state(() -> config.glowColor, v -> config.glowColor = v)
 *         .alpha(true)
 *         .build();
 * }</pre>
 *
 * <h3>The value</h3>
 *
 * An int, the consumer's: {@code get} is read every frame and {@code set} receives
 * every change. Without alpha it is {@code 0xRRGGBB} (the top byte is ignored when
 * read and zero when written); with {@code alpha(true)} it is {@code 0xAARRGGBB}. A
 * dye is opaque, so picking one writes alpha {@code FF}.
 *
 * <h3>The hex field</h3>
 *
 * Typing is free; only valid hex is written: {@code #RRGGBB}, and with alpha
 * {@code #AARRGGBB} too (six digits then keep the current transparency); the
 * {@code #} is optional. While the text is not valid it shows red and nothing is
 * written; when the field loses focus it goes back to the colour's hex.
 *
 * <h3>The kind of screen</h3>
 *
 * The swatch is a button of the screen's kind (vanilla's stone button on an options
 * screen, MenuKit's raised grey on a container screen) with the colour inside it,
 * over a checkerboard when it has alpha. The dialog is a panel on the same screen,
 * so it draws in the same kind, its tabs, buttons and hex field included. It needs a
 * screen: on the HUD the swatch only shows the colour.
 */
public class ColorField extends AbstractPanelElement {

    /** The swatch's size unless {@code size} says otherwise: a vanilla control's height, square. */
    public static final int DEFAULT_SIZE = 20;
    /** Room between the swatch's frame and the colour inside it. */
    private static final int INSET = 3;

    private final IntSupplier stateGet;
    private final IntConsumer stateSet;
    private final boolean alpha;
    private final Component label;

    // The vanilla stand-in for sound, focus and narration, made at the first attach.
    private @Nullable ElementWidget widget;
    private @Nullable Screen screen;

    /** The dialog, built once with the field; shown on whatever screen the field is on. */
    private final ColorDialog dialog;
    /** The dialog's view state: whether it is open, and the colour to go back to on Cancel. */
    private boolean open = false;
    private int before;

    protected ColorField(Builder b) {
        super(b);
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
        this.alpha = b.alpha;
        this.label = b.label;
        this.dialog = new ColorDialog(this);
    }

    /** Starts a colour field. Give it a {@code state(get, set)}. */
    public static Builder builder() {
        return new Builder();
    }

    // The value

    /** The colour the field shows now: {@code 0xRRGGBB}, or {@code 0xAARRGGBB} with alpha. */
    public int value() {
        int v = stateGet.getAsInt();
        return alpha ? v : v & 0xFFFFFF;
    }

    /** Whether the value carries alpha ({@code 0xAARRGGBB}). */
    public boolean hasAlpha() {
        return alpha;
    }

    /** Writes {@code argb} through the lens, without the alpha byte when the field has none. */
    void write(int argb) {
        stateSet.accept(alpha ? argb : argb & 0xFFFFFF);
    }

    /** The value as an opaque-or-not ARGB colour to draw. */
    int drawColor() {
        int v = value();
        return alpha ? v : 0xFF000000 | v;
    }

    /** What the setting is called: the dialog's title. */
    Component label() {
        return label;
    }

    /** The colour the dialog opened with (what Cancel writes back), as an ARGB colour to draw. */
    int before() {
        return alpha ? before : 0xFF000000 | before;
    }

    // Hex, public so consumers can read and write the same strings

    /**
     * The colour in {@code text}, or {@code null} when it is not valid hex: six digits
     * ({@code RRGGBB}, read as opaque with alpha), or with {@code alpha} eight
     * ({@code AARRGGBB}); a leading {@code #} and surrounding spaces are allowed.
     * Without alpha the result is {@code 0xRRGGBB}.
     */
    public static @Nullable Integer parseHex(@Nullable String text, boolean alpha) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6 && !(alpha && t.length() == 8)) return null;
        for (int i = 0; i < t.length(); i++) {
            if (Character.digit(t.charAt(i), 16) < 0) return null;
        }
        long v = Long.parseLong(t, 16);
        if (t.length() == 8) return (int) v;
        return alpha ? (int) (0xFF000000L | v) : (int) v;
    }

    /** {@code #RRGGBB}, or {@code #AARRGGBB} with alpha, in capitals. */
    public static String formatHex(int color, boolean alpha) {
        return alpha ? String.format(Locale.ROOT, "#%08X", color)
                : String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    /** A dye's colour, opaque {@code 0xAARRGGBB}: vanilla's own for it (dyed leather, sheep, banners). */
    public static int dyeColor(DyeColor dye) {
        return dye.getTextureDiffuseColor();
    }

    // The dialog. Public for MenuKit's checks; the swatch and the dialog's own
    // elements are what call these in play.

    /** Opens the dialog, remembering the colour to go back to. Does nothing while it is open. */
    @ApiStatus.Internal
    public void openDialog() {
        if (open) return;
        before = value();
        open = true;
        dialog.opened();
        // Focus leaves the swatch, so Enter in the dialog is not the swatch's.
        if (screen != null) Focus.clear(screen);
    }

    /** Whether the dialog is open. */
    @ApiStatus.Internal
    public boolean isDialogOpen() {
        return open;
    }

    /** Done: keeps the colour and closes the dialog. */
    @ApiStatus.Internal
    public void done() {
        open = false;
    }

    /** Cancel (and Escape): writes back the colour from before the dialog opened, and closes it. */
    @ApiStatus.Internal
    public void cancel() {
        if (!open) return;
        open = false;
        write(before);
    }

    /** A dye picked in the dialog: its colour, opaque, through the lens. */
    @ApiStatus.Internal
    public void pickDye(DyeColor dye) {
        write(dyeColor(dye));
    }

    /** What the hex field shows: the colour's hex, or what the player is typing. */
    @ApiStatus.Internal
    public String hexText() {
        return dialog.hexText();
    }

    /** Text typed into the hex field: kept as typed, and written only when it is valid hex. */
    @ApiStatus.Internal
    public void typeHex(String text) {
        dialog.typeHex(text);
    }

    /** Shows a tab of the dialog ({@code "basic"} or {@code "custom"}), as a click on it does. */
    @ApiStatus.Internal
    public void showDialogTab(String id) {
        dialog.showTab(id);
    }

    /**
     * What the dialog's shown tab draws, each {@code {x, y, width, height}} from the
     * tab's body: every dye's slot with its ring (in {@link #dyeRows} order), or the
     * picker and the hex field.
     */
    @ApiStatus.Internal
    public List<int[]> dialogCells() {
        return dialog.shownCells();
    }

    /** The dye grid's rows, top to bottom. */
    @ApiStatus.Internal
    public static List<List<DyeColor>> dyeRows() {
        return ColorDialog.DYE_ROWS;
    }

    /** A dye's tooltip: vanilla's translated colour name. */
    @ApiStatus.Internal
    public static Component dyeName(DyeColor dye) {
        return ColorDialog.dyeName(dye);
    }

    /** The dialog's panel: a modal on the screen the field is on, open while {@link #isDialogOpen}. */
    @ApiStatus.Internal
    public Panel dialog() {
        return dialog.panel();
    }

    /** Whether the dialog should show this frame. */
    boolean dialogShown() {
        return open;
    }

    // The swatch

    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        boolean hovered = isHovered(ctx);
        if (widget != null) {
            widget.track(x, y, width, height, hovered, !disabled,
                    Component.empty().append(label).append(" ").append(formatHex(value(), alpha)));
        }
        boolean hot = !disabled && (hovered || (widget != null && widget.focused()));
        GuiGraphicsExtractor g = ctx.graphics();
        if (ctx.kind() == ScreenKind.OPTIONS) {
            VanillaLook.button(g, x, y, width, height, !disabled, hot);
        } else {
            PanelRendering.renderPanel(g, x, y, width, height, disabled ? PanelStyle.DARK : PanelStyle.RAISED);
            if (hot) g.fill(x + 1, y + 1, x + width - 1, y + height - 1, ElementConstants.HOVER_OVERLAY);
        }
        int ix = x + INSET, iy = y + INSET, iw = width - 2 * INSET, ih = height - 2 * INSET;
        if (iw > 0 && ih > 0) {
            g.fill(ix - 1, iy - 1, ix + iw + 1, iy + ih + 1, 0xFF000000);
            if (alpha) ColorDialog.checkerboard(g, ix, iy, iw, ih);
            g.fill(ix, iy, ix + iw, iy + ih, drawColor());
            if (disabled) g.fill(ix, iy, ix + iw, iy + ih, ElementConstants.DISABLED_OVERLAY);
        }
        if (hovered) queueTooltip(ctx);
    }

    /** Left click opens the dialog. */
    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (button != Click.LEFT || disabled(in)) return false;
        ElementWidget.playClickSound();
        openDialog();
        return true;
    }

    /** Enter or Space on the focused swatch (vanilla played the sound). */
    private void press() {
        if (!ownDisabled()) openDialog();
    }

    /**
     * Joins the screen: the swatch's stand-in, and the dialog on the screen's dialog
     * host, so it can open over whatever screen the field is on. A field moved to
     * another screen starts with its dialog closed.
     */
    @Override
    public void onAttach(Screen screen) {
        if (widget == null) widget = new ElementWidget(this::press);
        widget.attach(screen);
        if (this.screen != screen) open = false;
        this.screen = screen;
        ScreenPanelRegistry.attachDialog(screen, dialog.panel());
    }

    @Override
    public void onDetach(Screen screen) {
        if (widget != null) widget.detach(screen);
        ScreenPanelRegistry.detachDialog(screen, dialog.panel());
    }

    /** Builds a {@link ColorField}. {@code state(get, set)} is required. */
    public static class Builder extends AbstractPanelElement.Builder<ColorField, Builder> {
        private @Nullable IntSupplier stateGet;
        private @Nullable IntConsumer stateSet;
        private boolean alpha = false;
        private Component label = Component.literal("Color");

        protected Builder() {
            this.width = DEFAULT_SIZE;
            this.height = DEFAULT_SIZE;
        }

        @Override protected Builder self() { return this; }

        /** The swatch's size in pixels. Default 20 x 20. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /**
         * Required: the lens onto the colour. {@code get} is read every frame;
         * {@code set} receives every change the player makes, and the opening colour
         * again on Cancel.
         */
        public Builder state(IntSupplier get, IntConsumer set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        /** Whether the value carries transparency ({@code 0xAARRGGBB}). Default false ({@code 0xRRGGBB}). */
        public Builder alpha(boolean alpha) {
            this.alpha = alpha;
            return this;
        }

        /** What the setting is called: the dialog's title, and what the narrator reads. Default "Color". */
        public Builder label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public ColorField build() {
            require(stateGet != null, "state(get, set) is required; a ColorField shows the consumer's colour");
            require(width > 0 && height > 0, "size(w, h) must be positive");
            // Hovered, the swatch reads its hex unless the consumer says something else.
            if (tooltip == null) {
                IntSupplier get = stateGet;
                boolean a = alpha;
                tooltip = () -> Component.literal(formatHex(a ? get.getAsInt() : get.getAsInt() & 0xFFFFFF, a));
            }
            return new ColorField(this);
        }
    }
}
