package com.trevlar.menukit.core;

import com.trevlar.menukit.api.element.ElementConstants;
import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * How MenuKit's raised controls draw, in both looks, under the active resource packs. A
 * control draws from one of two <b>sets</b> of sprites, named as vanilla names its button's
 * ({@code _highlighted}, {@code _pressed}, {@code _disabled}): MenuKit's own
 * {@code menukit:control_raised} set, or vanilla's {@code widget/button} set (which has no
 * pressed sprite). Which set:
 * <ul>
 *   <li>the Default look: vanilla's, always;</li>
 *   <li>the Panel look, per control: MenuKit's where a pack overrides any of it; else
 *       vanilla's where a pack restyled any of vanilla's (every Panel-look element follows
 *       its vanilla equivalent, and takes vanilla's text colors on it: {@link #kind}); else
 *       MenuKit's, so an unmodded game is unchanged to the pixel.</li>
 * </ul>
 *
 * <h3>One rule for every set</h3>
 * Per state: the set's own sprite for it where it has one (from a pack, or the set's own art
 * when no pack touched its normal sprite); otherwise derived from the set's normal sprite, so
 * a control never switches look between states. The derivations, at blit time:
 * <ul>
 *   <li><b>Highlighted</b>: the normal sprite, then a white wash ({@link #HOVER_WASH}) inside
 *       its 1 px outline (MenuKit's own highlighted sprite is exactly this).</li>
 *   <li><b>Disabled</b>: the normal sprite multiplied by {@link #DISABLED_TINT}: darker reads
 *       as off on every pack.</li>
 *   <li><b>Pressed</b>: the face under the cursor (highlighted while hovered) with MenuKit's
 *       one pressed transform over it ({@link #pressedOverlay}): an inverted bevel and a wash
 *       that darkens a light face and lightens a dark one. How a control is pressed is its
 *       {@link Press}.</li>
 * </ul>
 * With no pack every state of both sets is the set's own sprite, so both looks draw what
 * they drew before packs were followed.
 *
 * <h3>The sprite table</h3>
 * {@link Control} is the one place the sprites MenuKit's looks draw are named: each element
 * kind, MenuKit's own sprites for it and vanilla's equivalent. Elements whose vanilla
 * equivalent is already a vanilla widget (slider, text field, tabs, the scroller handle)
 * draw it in both looks and follow a pack as they are; elements with no vanilla equivalent
 * (radio, info box, a card, a progress bar) keep MenuKit's art.
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

    /**
     * How a raised control is pressed.
     * <ul>
     *   <li>{@link #NONE}: not at all.</li>
     *   <li>{@link #HELD}: held down, between press and release (a button). The press is under
     *       the cursor, so a derived press is over the highlighted face; the set's own pressed
     *       sprite shows as it is.</li>
     *   <li>{@link #ON}: latched (a toggle that is on). A derived press is over the face as
     *       hovered or not; the set's own pressed sprite takes the hover wash while hovered.</li>
     *   <li>{@link #OPEN}: a dropdown's trigger while its list is up, never hovered (the list
     *       is the live surface). Vanilla's set presses its resting face, as the Default look
     *       always has; MenuKit's leaves it resting.</li>
     * </ul>
     */
    public enum Press { NONE, HELD, ON, OPEN }

    /** The highlighted derivation: a white wash inside the outline (MenuKit's own hover). */
    public static final int HOVER_WASH = ElementConstants.HOVER_OVERLAY;
    /** The disabled derivation: the normal sprite at 0.3 of its brightness, its alpha kept. */
    public static final int DISABLED_TINT = 0xFF4D4D4D;

    /** The pressed transform's inverted bevel: translucent black over the top and left, translucent white over the bottom and right. */
    public static final int PRESSED_SHADE = 0xAE000000, PRESSED_LIGHT = 0x45FFFFFF;
    /** The wash inside it: a quarter of the way to black on a light face, to white on a dark one. */
    public static final int PRESSED_DARKEN = 0x40000000, PRESSED_LIGHTEN = 0x40FFFFFF;
    /**
     * A face that reads darker than this gray ({@code 0xFF606060}, by {@link PackArt#luminance})
     * is lit by the press instead of darkened: between a typical dark-mode pack's button and
     * vanilla's, so vanilla's own darkens as it always has.
     */
    public static final double DARK_FACE = PackArt.luminance(0xFF606060);

    /**
     * The sprites MenuKit's looks draw, per element kind: MenuKit's own (a pack overrides them
     * to theme MenuKit directly) and vanilla's equivalent (which nearly every pack restyles).
     * The only place these sprites are named.
     */
    public enum Control {
        /** Buttons, toggles, a dropdown's trigger and list, the color field, the item dialog's row controls; by {@link State}. */
        BUTTON(states("menukit", "control_raised", true), states("minecraft", "widget/button", false)),
        /** The checkbox's box: vanilla's by checked and highlighted ({@link #checkbox}); MenuKit's is drawn with fills. */
        CHECKBOX(null, vanilla("widget/checkbox", "widget/checkbox_highlighted", "widget/checkbox_selected",
                "widget/checkbox_selected_highlighted")),
        /** A Panel-look panel surface: MenuKit's raised panel (its look cut from container art is {@link PackLook}'s). */
        PANEL(new Identifier[]{Identifier.fromNamespaceAndPath("menukit", "panel_raised")}, null),
        /** A slot frame: vanilla's slot sprite (horse, nautilus, stats; the Default look's slot). */
        SLOT(null, vanilla("container/slot"));

        private final @Nullable Identifier @Nullable [] own;
        private final @Nullable Identifier @Nullable [] vanilla;

        Control(@Nullable Identifier @Nullable [] own, @Nullable Identifier @Nullable [] vanilla) {
            this.own = own;
            this.vanilla = vanilla;
        }

        /** MenuKit's own sprite {@code i} for this element ({@link State#ordinal} for a raised control). */
        public Identifier own(int i) {
            return Objects.requireNonNull(Objects.requireNonNull(own)[i]);
        }

        /** Vanilla's sprite {@code i} for this element ({@link State#ordinal} for a raised control, never pressed). */
        public Identifier vanilla(int i) {
            return Objects.requireNonNull(Objects.requireNonNull(vanilla)[i]);
        }

        /** Vanilla's checkbox sprite for a box {@code checked} or not, {@code highlighted} or not. */
        public static Identifier checkbox(boolean checked, boolean highlighted) {
            return CHECKBOX.vanilla((checked ? 2 : 0) + (highlighted ? 1 : 0));
        }

        private static @Nullable Identifier[] states(String namespace, String base, boolean pressed) {
            @Nullable Identifier[] ids = new Identifier[State.values().length];
            for (State s : State.values()) {
                if (s != State.PRESSED || pressed) ids[s.ordinal()] = Identifier.fromNamespaceAndPath(namespace, base + s.suffix);
            }
            return ids;
        }

        private static Identifier[] vanilla(String... paths) {
            return Arrays.stream(paths).map(Identifier::withDefaultNamespace).toArray(Identifier[]::new);
        }
    }

    /** A sprite's file, under {@code textures/gui/sprites/}: what a pack replaces. */
    public static Identifier file(Identifier sprite) {
        return sprite.withPath(p -> "textures/gui/sprites/" + p + ".png");
    }

    /** One set as drawn now: vanilla's or MenuKit's, and each state's sprite ({@code null}: derived). */
    private record Art(boolean vanilla, @Nullable Identifier @Nullable [] sprites) {}

    // One answer per control and look, dropped whole on a resource reload.
    private static final Map<Control, Art> PANEL_ART = new ConcurrentHashMap<>(), DEFAULT_ART = new ConcurrentHashMap<>();

    /** A resource reload: every control is decided again from what the new packs supply. */
    public static void onReload() {
        PANEL_ART.clear();
        DEFAULT_ART.clear();
        FACES.clear();
    }

    /**
     * The look {@code control} draws in on a screen of kind {@code screen}: the Default look
     * where the screen is one, or where the Panel look draws vanilla's set for it; else
     * {@code screen}. Elements color their text by what this returns in place of the
     * screen's kind.
     */
    public static ScreenKind kind(ScreenKind screen, Control control) {
        return screen == ScreenKind.PANEL && follows(control) ? ScreenKind.DEFAULT : screen;
    }

    /** Whether {@code control} draws vanilla's sprites in the Panel look now. */
    public static boolean follows(Control control) {
        return art(ScreenKind.PANEL, control).vanilla();
    }

    /** Each state's sprite for {@code control} in the Panel look now, by {@link State}; {@code null} where it is derived. */
    public static @Nullable Identifier[] sprites(Control control) {
        @Nullable Identifier[] s = art(ScreenKind.PANEL, control).sprites();
        return s == null ? new Identifier[0] : s.clone();
    }

    private static Art art(ScreenKind kind, Control control) {
        return kind == ScreenKind.DEFAULT ? DEFAULT_ART.computeIfAbsent(control, c -> decide(c, true))
                : PANEL_ART.computeIfAbsent(control, c -> decide(c, false));
    }

    /**
     * One control's set, in the class doc's order, then each state's sprite. No game
     * (MenuKit's headless checks): the set's own art, MenuKit's in the Panel look.
     */
    private static Art decide(Control c, boolean defaultLook) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return defaultLook ? new Art(true, c.vanilla) : new Art(false, c.own);
        ResourceManager rm = mc.getResourceManager();
        Predicate<Identifier> ownFromPack = id -> PackArt.fromPack(rm.getResource(file(id)), PackArt.MENUKIT_PACK);
        Predicate<Identifier> vanillaFromPack = id -> PackArt.fromPack(rm.getResource(file(id)), PackArt.VANILLA_PACK);
        boolean vanilla = defaultLook || (!anyOf(c.own, ownFromPack) && anyOf(c.vanilla, vanillaFromPack));
        if (vanilla) return new Art(true, c.vanilla == null ? null : pick(c.vanilla, vanillaFromPack));
        return new Art(false, c.own == null ? null : pick(c.own, ownFromPack));
    }

    private static boolean anyOf(@Nullable Identifier @Nullable [] ids, Predicate<Identifier> fromPack) {
        if (ids == null) return false;
        for (Identifier id : ids) if (id != null && fromPack.test(id)) return true;
        return false;
    }

    /**
     * Each state's sprite for one set, by {@link State}: per state, its sprite where a pack
     * supplies it or where the normal sprite is the set's own; {@code null} (derived from the
     * normal sprite) where a pack supplied the normal one and not this, or where the set has
     * no sprite for it. {@code fromPack}: whether a pack, not the set's own, supplies a
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

    // Drawing: the one path every raised control takes, in both looks

    /**
     * A raised control's background on a screen of kind {@code kind}, in its state: disabled
     * wins (a toggle on and disabled is the disabled face pressed, so on and off stay apart);
     * then {@code press}; then hovered; else resting.
     */
    public static void drawRaised(ScreenKind kind, GuiGraphicsExtractor g, int x, int y, int w, int h,
                                  boolean enabled, boolean hovered, Press press) {
        if (w <= 0 || h <= 0) return;
        Art a = art(kind, Control.BUTTON);
        if (!enabled) {
            draw(g, a, x, y, w, h, State.DISABLED);
            if (press == Press.ON || (press == Press.OPEN && a.vanilla())) pressedOverlay(g, x, y, w, h, face(a, State.DISABLED));
            return;
        }
        Identifier pressedSprite = a.sprites()[State.PRESSED.ordinal()];
        switch (press) {
            case NONE -> draw(g, a, x, y, w, h, hovered ? State.HIGHLIGHTED : State.NORMAL);
            case OPEN -> {
                draw(g, a, x, y, w, h, State.NORMAL);
                if (a.vanilla()) pressedOverlay(g, x, y, w, h, face(a, State.NORMAL));
            }
            case HELD, ON -> {
                if (pressedSprite != null) {
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, pressedSprite, x, y, w, h);
                    if (press == Press.ON && hovered) wash(g, x, y, w, h);
                } else {
                    State under = press == Press.HELD || hovered ? State.HIGHLIGHTED : State.NORMAL;
                    draw(g, a, x, y, w, h, under);
                    pressedOverlay(g, x, y, w, h, face(a, under));
                }
            }
        }
    }

    /**
     * An open dropdown's list on a screen of kind {@code kind}: a flat surface under the
     * trigger. Vanilla's set draws its disabled face, its darker and flatter gray (as the
     * Default look always has); MenuKit's its resting face.
     */
    public static void drawList(ScreenKind kind, GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        Art a = art(kind, Control.BUTTON);
        draw(g, a, x, y, w, h, a.vanilla() ? State.DISABLED : State.NORMAL);
    }

    /**
     * MenuKit's press over vanilla's own button as it draws under the mouse (its highlighted
     * face): the press on a vanilla or YACL button, MenuKit's one styling exception there.
     */
    public static void pressVanillaButton(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        pressedOverlay(g, x, y, w, h, face(art(ScreenKind.DEFAULT, Control.BUTTON), State.HIGHLIGHTED));
    }

    /** One state of a set's art, never pressed: its sprite, or derived from the normal one (see the class doc). */
    private static void draw(GuiGraphicsExtractor g, Art a, int x, int y, int w, int h, State state) {
        @Nullable Identifier[] sprites = a.sprites();
        Identifier sprite = sprites[state.ordinal()], normal = sprites[State.NORMAL.ordinal()];
        if (sprite != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        } else if (state == State.DISABLED) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, normal, x, y, w, h, DISABLED_TINT);
        } else {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, normal, x, y, w, h);
            if (state == State.HIGHLIGHTED) wash(g, x, y, w, h);
        }
    }

    private static void wash(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HOVER_WASH);
    }

    /**
     * MenuKit's one pressed transform, drawn over the face the control shows under it, for
     * every derived press in both looks and on every pack. Relative to that face, so it reads
     * on light, dark and colored faces alike:
     * <ul>
     *   <li>the 1 px bevel inside the outline inverted: translucent black over the top and
     *       left, translucent white over the bottom and right;</li>
     *   <li>a wash inside the bevel that darkens a light face by a quarter and lightens a dark
     *       one ({@link #DARK_FACE}), since a near-black face can't be darkened further.</li>
     * </ul>
     * The alphas are tuned so vanilla's highlighted button comes out as it did under the fixed
     * grays drawn before (0xFF373737 top and left, 0xFF888888 bottom and right): the bevel
     * lines within a few levels, the wash exact. The outer outline is left alone.
     *
     * @param face the luminance of the face it lands on ({@link PackArt#luminance})
     */
    public static void pressedOverlay(GuiGraphicsExtractor g, int x, int y, int width, int height, double face) {
        // Each bevel pixel is washed once: the top row stops short of the right column and the
        // left column short of the bottom row, which take those corners, as the grays did.
        g.fill(x + 1, y + 1, x + width - 2, y + 2, PRESSED_SHADE);
        g.fill(x + 1, y + 2, x + 2, y + height - 2, PRESSED_SHADE);
        g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, PRESSED_LIGHT);
        g.fill(x + width - 2, y + 1, x + width - 1, y + height - 2, PRESSED_LIGHT);
        g.fill(x + 2, y + 2, x + width - 2, y + height - 2, face < DARK_FACE ? PRESSED_LIGHTEN : PRESSED_DARKEN);
    }

    // The face a press lands on

    /** How a sprite reads when it can't be read (no game, no such sprite, a broken file): light, so a press darkens it. */
    private static final int LIGHT_FACE = 0xFFFFFFFF;
    // One face color per sprite, failures included, dropped on a resource reload.
    private static final Map<Identifier, Integer> FACES = new ConcurrentHashMap<>();

    /** The luminance of a set's face in {@code state}: its sprite's, or as derived from the normal one. */
    private static double face(Art a, State state) {
        @Nullable Identifier[] sprites = a.sprites();
        if (sprites[state.ordinal()] != null) return PackArt.luminance(faceColor(sprites[state.ordinal()]));
        int normal = faceColor(sprites[State.NORMAL.ordinal()]);
        int derived = switch (state) {
            case DISABLED -> multiply(normal, DISABLED_TINT);
            case HIGHLIGHTED -> over(HOVER_WASH, normal);
            default -> normal;
        };
        return PackArt.luminance(derived);
    }

    /** A sprite's face color ({@link PackArt#faceColor}), read once per reload. */
    private static int faceColor(Identifier sprite) {
        return FACES.computeIfAbsent(sprite, s -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return LIGHT_FACE;
            Optional<Resource> res = mc.getResourceManager().getResource(file(s));
            Integer c = res.isEmpty() ? null : PackArt.faceColor(res.get());
            return c == null ? LIGHT_FACE : c;
        });
    }

    /** {@code c} with each channel multiplied by {@code tint}'s, as a tinted blit draws it. */
    private static int multiply(int c, int tint) {
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) out |= (((c >> s) & 0xFF) * ((tint >> s) & 0xFF) / 255) << s;
        return out;
    }

    /** {@code top} (translucent) blended over opaque {@code under}. */
    private static int over(int top, int under) {
        int a = top >>> 24, out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) out |= ((((top >> s) & 0xFF) * a + ((under >> s) & 0xFF) * (255 - a)) / 255) << s;
        return out;
    }
}
