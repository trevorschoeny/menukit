package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.ScreenKind;

import com.trevlar.menukit.api.element.ElementConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Which art a Panel-look control draws under the active resource packs (spec part 10,
 * "every Panel-look element follows its vanilla equivalent"). The Panel look draws
 * MenuKit's own art, which no pack touches; the Default look draws vanilla's widget
 * sprites, which nearly every pack restyles. So each control is mapped, once, here, to its
 * vanilla equivalent, and per control (spec part 1's order, as for panels):
 * <ol>
 *   <li>a pack overrides MenuKit's own sprite for it: MenuKit's drawing, with that sprite;</li>
 *   <li>otherwise, a pack restyled any of its vanilla sprites: it draws as in the Default
 *       look, vanilla's sprites and vanilla's text colours for that control (white on a
 *       button, light grey disabled), through {@link VanillaLook} and the element's own
 *       Default-look code;</li>
 *   <li>otherwise MenuKit's art, so an unmodded game is unchanged to the pixel.</li>
 * </ol>
 * Any one sprite of a control's set being a pack's makes the whole set that control draws.
 *
 * <h3>Every state follows the normal state's source (spec part 12)</h3>
 * A raised control's sprites come in a set named as vanilla names its button's:
 * {@code menukit:control_raised}, {@code _highlighted}, {@code _pressed}, {@code _disabled}
 * (vanilla's {@code widget/button} set has no pressed sprite). Per state, in the set drawn:
 * <ol>
 *   <li>a pack supplies that state's sprite: it;</li>
 *   <li>otherwise, if a pack supplied the normal sprite: derived from that sprite, so a
 *       control never switches look between states;</li>
 *   <li>otherwise the set's own art (MenuKit's, split from what it drew before, so an
 *       unmodded game is unchanged to the pixel; or vanilla's own).</li>
 * </ol>
 * The derivations, one for every control and every pack, all at blit time (no generated
 * texture):
 * <ul>
 *   <li><b>Highlighted</b>: the normal sprite, then a white wash ({@link #HOVER_WASH}) inside
 *       its 1 px outline. MenuKit's own hover, so MenuKit's highlighted sprite is exactly
 *       this transform of its normal one; it lightens a light, dark or coloured fill alike.</li>
 *   <li><b>Disabled</b>: the normal sprite multiplied by {@link #DISABLED_TINT}, the bevel and
 *       hue kept, the whole darker. Vanilla's disabled button is its normal one at about
 *       0.4 of the brightness, flattened; MenuKit's is darker still; this sits between. Darker
 *       reads as off on every pack (a dark one goes near black, as vanilla's own disabled
 *       button is), where fading would leave a light pack's button barely changed and let what
 *       is under an open list show through it.</li>
 *   <li><b>Pressed</b>: MenuKit's pressed look on the resting or hovered sprite
 *       ({@link VanillaLook#pressedOverlay}), as the Default look presses vanilla's button.</li>
 * </ul>
 *
 * <h3>The mapping</h3>
 * <ul>
 *   <li><b>Button, Toggle, a dropdown's trigger and its open list, ColorField, the item
 *       dialog's row controls</b>: MenuKit's {@code menukit:control_raised};
 *       {@link Control#BUTTON}, vanilla's {@code widget/button}, {@code _highlighted},
 *       {@code _disabled} (an open list is {@code _disabled}, as in the Default look). A
 *       Toggle that is on is the button pressed, since vanilla's button has no on state and
 *       a Panel-look toggle's label need not say its state.</li>
 *   <li><b>Checkbox's box</b>: drawn with fills; {@link Control#CHECKBOX}, vanilla's
 *       {@code widget/checkbox}, {@code _highlighted}, {@code _selected},
 *       {@code _selected_highlighted}.</li>
 *   <li><b>Slider, text field, Tabs</b>: already vanilla's widgets and sprites
 *       ({@code widget/slider*}, {@code widget/text_field*}, {@code widget/tab*}) in both
 *       looks, so they follow a pack as they are.</li>
 *   <li><b>Scrollbar</b>: its handle is already vanilla's
 *       {@code container/creative_inventory/scroller}; its track has no sprite of vanilla's
 *       (the creative screen draws its track into its tab art) and keeps MenuKit's.</li>
 *   <li><b>A slot on a plain panel; a dialog</b>: panel surfaces, cut from the pack's
 *       chest, else {@code container/slot} ({@link PackLook}).</li>
 *   <li><b>Radio, InfoBox, a Flow card, ProgressBar</b>: no vanilla equivalent; MenuKit's
 *       art, their text reading on the panel under them.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class ControlLook {

    private ControlLook() {}

    /** A raised control's states, in the order of each set's sprites. */
    public enum State {
        NORMAL(""), HIGHLIGHTED("_highlighted"), PRESSED("_pressed"), DISABLED("_disabled");

        private final String suffix;

        State(String suffix) { this.suffix = suffix; }
    }

    /** The highlighted derivation: a white wash inside the outline (MenuKit's own hover). */
    public static final int HOVER_WASH = ElementConstants.HOVER_OVERLAY;
    /** The disabled derivation: the normal sprite at 0.3 of its brightness, its alpha kept. */
    public static final int DISABLED_TINT = 0xFF4D4D4D;

    /** A Panel-look control with a vanilla equivalent: MenuKit's own sprites for it, if any, and vanilla's. */
    public enum Control {
        /** By {@link State}: MenuKit's {@code control_raised} set; vanilla's button set (no pressed sprite). */
        BUTTON(states("menukit", "control_raised", true), states("minecraft", "widget/button", false)),
        CHECKBOX(null, sprites("widget/checkbox", "widget/checkbox_highlighted", "widget/checkbox_selected",
                "widget/checkbox_selected_highlighted"));

        private final Identifier @Nullable [] own;
        private final @Nullable Identifier[] vanilla;

        Control(Identifier @Nullable [] own, @Nullable Identifier[] vanilla) {
            this.own = own;
            this.vanilla = vanilla;
        }

        private static @Nullable Identifier[] states(String namespace, String base, boolean pressed) {
            @Nullable Identifier[] ids = new Identifier[State.values().length];
            for (State s : State.values()) {
                if (s != State.PRESSED || pressed) ids[s.ordinal()] = Identifier.fromNamespaceAndPath(namespace, base + s.suffix);
            }
            return ids;
        }

        private static Identifier[] sprites(String... paths) {
            return Arrays.stream(paths).map(Identifier::withDefaultNamespace).toArray(Identifier[]::new);
        }
    }

    /** What one control draws now: vanilla's set or MenuKit's, and each state's sprite ({@code null}: derived). */
    private record Art(boolean vanilla, @Nullable Identifier @Nullable [] sprites) {}

    // ponytail: one answer per control, dropped whole on a resource reload (PackLook.onReload).
    private static final Map<Control, Art> ART = new ConcurrentHashMap<>();

    /** A resource reload: every control is decided again from what the new packs supply. */
    static void onReload() {
        ART.clear();
    }

    /**
     * The look {@code control} draws in on a screen of kind {@code screen}: the Default look
     * where the screen is one, or where a pack restyled the control's vanilla sprites and not
     * MenuKit's own; else {@code screen}. Elements colour their text by what this returns in
     * place of the screen's kind.
     */
    public static ScreenKind kind(ScreenKind screen, Control control) {
        return screen == ScreenKind.PANEL && follows(control) ? ScreenKind.DEFAULT : screen;
    }

    /** Whether {@code control} draws vanilla's sprites in the Panel look now. */
    public static boolean follows(Control control) {
        return art(control).vanilla();
    }

    /** Each state's sprite for {@code control} now, by {@link State}; {@code null} where it is derived. */
    public static @Nullable Identifier[] sprites(Control control) {
        @Nullable Identifier[] s = art(control).sprites();
        return s == null ? new Identifier[0] : s.clone();
    }

    private static Art art(Control control) {
        return ART.computeIfAbsent(control, ControlLook::decide);
    }

    /** Spec part 10's order for one control, then part 12's per state; no game (the headless checks): MenuKit's art. */
    private static Art decide(Control c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return new Art(false, c.own);
        ResourceManager rm = mc.getResourceManager();
        Predicate<Identifier> ownFromPack = id -> PackLook.fromPack(rm.getResource(file(id)), PackLook.MENUKIT_PACK);
        Predicate<Identifier> vanillaFromPack = id -> PackLook.fromPack(rm.getResource(file(id)), PackLook.VANILLA_PACK);
        boolean vanilla = !anyOf(c.own, ownFromPack) && anyOf(c.vanilla, vanillaFromPack);
        if (vanilla) return new Art(true, c.own == null ? null : pick(c.vanilla, vanillaFromPack));
        return new Art(false, c.own == null ? null : pick(c.own, ownFromPack));
    }

    private static boolean anyOf(@Nullable Identifier @Nullable [] ids, Predicate<Identifier> fromPack) {
        if (ids == null) return false;
        for (Identifier id : ids) if (id != null && fromPack.test(id)) return true;
        return false;
    }

    /**
     * Spec part 12 for one set of state sprites, by {@link State}: per state, its sprite where
     * a pack supplies it or where the normal sprite is the set's own; {@code null} (derived
     * from the normal sprite) where a pack supplied the normal one and not this, or where the
     * set has no sprite for it. {@code fromPack}: whether a pack, not the set's own, supplies a
     * sprite. Pure, for the headless checks.
     */
    public static @Nullable Identifier[] pick(@Nullable Identifier[] set, Predicate<Identifier> fromPack) {
        boolean normalFromPack = fromPack.test(set[State.NORMAL.ordinal()]);
        @Nullable Identifier[] out = new Identifier[set.length];
        for (int i = 0; i < set.length; i++) {
            Identifier id = set[i];
            out[i] = id != null && (!normalFromPack || fromPack.test(id)) ? id : null;
        }
        return out;
    }

    private static Identifier file(Identifier sprite) {
        return sprite.withPath(p -> "textures/gui/sprites/" + p + ".png");
    }

    // Drawing: the one path every raised control takes in the Panel look

    /**
     * A raised control's background in the Panel look, in its state: disabled wins; then
     * pressed (the pressed sprite, washed while hovered; or derived, MenuKit's pressed look on
     * the resting or hovered sprite); then hovered; else resting. Buttons, toggles (on is
     * pressed), a dropdown's trigger, the colour field and the item dialog's row controls.
     */
    public static void drawRaised(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                  boolean enabled, boolean hovered, boolean pressed) {
        if (w <= 0 || h <= 0) return;
        if (!enabled) { draw(g, x, y, w, h, State.DISABLED); return; }
        if (!pressed) { draw(g, x, y, w, h, hovered ? State.HIGHLIGHTED : State.NORMAL); return; }
        Identifier sprite = art(Control.BUTTON).sprites()[State.PRESSED.ordinal()];
        if (sprite == null) {
            draw(g, x, y, w, h, hovered ? State.HIGHLIGHTED : State.NORMAL);
            VanillaLook.pressedOverlay(g, x, y, w, h);
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        if (hovered) wash(g, x, y, w, h);
    }

    /** One state of the raised control's art: its sprite, or derived from the normal one (see the class doc). */
    public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, State state) {
        if (w <= 0 || h <= 0) return;
        @Nullable Identifier[] sprites = art(Control.BUTTON).sprites();
        Identifier sprite = sprites[state.ordinal()], normal = sprites[State.NORMAL.ordinal()];
        if (sprite != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
            return;
        }
        switch (state) {
            case DISABLED -> g.blitSprite(RenderPipelines.GUI_TEXTURED, normal, x, y, w, h, DISABLED_TINT);
            case HIGHLIGHTED -> {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, normal, x, y, w, h);
                wash(g, x, y, w, h);
            }
            default -> {   // PRESSED
                g.blitSprite(RenderPipelines.GUI_TEXTURED, normal, x, y, w, h);
                VanillaLook.pressedOverlay(g, x, y, w, h);
            }
        }
    }

    private static void wash(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HOVER_WASH);
    }
}
