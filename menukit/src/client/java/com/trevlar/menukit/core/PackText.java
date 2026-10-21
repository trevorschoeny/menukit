package com.trevlar.menukit.core;

import com.trevlar.menukit.api.element.ElementConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text that reads on a Panel-look panel surface under the active resource packs. Body and
 * muted text keep MenuKit's colours wherever they read on the surface's fill, and take one
 * made from the fill where a pack's fill is too dark or too coloured for them. A dark-mode
 * pack whose text shader repaints vanilla's grey light is left to do so.
 */
@ApiStatus.Internal
public final class PackText {

    private PackText() {}

    private static final Logger LOG = LoggerFactory.getLogger("MenuKit/PackText");

    /** The Panel look's heading and body text, vanilla's label grey. */
    private static final int TEXT = ElementConstants.TEXT_DARK;
    /** MenuKit's muted text. */
    private static final int MUTED = ElementConstants.TEXT_MUTED;
    private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;

    /**
     * How far body text stands out from its fill: WCAG's 4.5:1 for text. Vanilla's grey stays
     * wherever it does, and text that has to change is made to: below it vanilla's grey reads
     * dim (on a lime shulker box, about 3:1).
     */
    public static final double BODY_TARGET = 4.5;
    /** How far muted text stands out from MenuKit's own fill: what muted text keeps on any fill. */
    private static final double MUTED_CONTRAST = contrast(MUTED, PackLook.SPRITE_FILL);

    /** A resource reload: the shader is read again and every text colour worked out again. */
    public static void onReload() {
        MEMO.clear();
        shaderLightens = 0;
    }

    /**
     * Heading and body text {@code text} on {@code fill}, pure. {@code text} itself wherever
     * it reads ({@link #BODY_TARGET}), so vanilla's grey stays on MenuKit's own fill and on any
     * light one. On a fill too dark or too coloured for it, a colour made from the fill: the
     * fill taken toward white (on a dark fill) or black (on a light or coloured one, an orange
     * chest) until it stands out by {@link #BODY_TARGET}, so the text keeps the fill's hue.
     *
     * <p>{@code shaderLightens} says the pack's text shader repaints vanilla's exact grey in
     * a light colour (as dark-mode packs do). Then on a dark fill vanilla's grey is left as
     * it is, for the pack to repaint as it does on its own screens: lightened once, by the pack.
     */
    public static int textOn(int text, int fill, boolean shaderLightens) {
        if (contrast(text, fill) >= BODY_TARGET) return text;
        boolean dark = darkFill(fill);
        if (dark && shaderLightens && text == TEXT) return text;
        return standingOut(fill, dark ? WHITE : BLACK, BODY_TARGET);
    }

    /**
     * Muted text on {@code fill}, pure: MenuKit's muted grey wherever it stands out from the
     * fill as much as it does from MenuKit's own fill (so exactly as before with no pack, and
     * on a dark-mode pack's dark grey), else the fill taken toward white or black until it
     * does: between the fill and the body text, in the fill's hue.
     */
    public static int mutedOn(int fill) {
        if (contrast(MUTED, fill) >= MUTED_CONTRAST) return MUTED;
        return standingOut(fill, darkFill(fill) ? WHITE : BLACK, MUTED_CONTRAST);
    }

    /** Whether text reads better light than dark on {@code fill}. */
    static boolean darkFill(int fill) {
        return contrast(fill, WHITE) > contrast(fill, BLACK);
    }

    /** WCAG's contrast ratio of two colours, 1 to 21. */
    public static double contrast(int a, int b) {
        double la = PackArt.luminance(a), lb = PackArt.luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** {@code fill} blended toward {@code toward} until it stands out from {@code fill} by {@code target}; {@code toward} if it never does. */
    private static int standingOut(int fill, int toward, double target) {
        for (int i = 1; i <= 100; i++) {
            int c = mix(fill, toward, i / 100.0);
            if (contrast(c, fill) >= target) return c;
        }
        return toward;
    }

    /** {@code a} blended toward {@code b} by {@code t}, opaque. */
    private static int mix(int a, int b, double t) {
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            out |= (int) Math.round(((a >> s) & 0xFF) * (1 - t) + ((b >> s) & 0xFF) * t) << s;
        }
        return out;
    }

    // The pack's text shader

    /** The GUI text shader's two halves, which a dark-mode pack replaces to repaint vanilla's text. */
    private static final List<Identifier> TEXT_SHADER = List.of(Identifier.withDefaultNamespace("shaders/core/text.fsh"),
            Identifier.withDefaultNamespace("shaders/core/text.vsh"));
    /** {@link #shaderLightens}, read once per reload: {@code 0} unread, {@code 1} no, {@code 2} yes. */
    private static volatile int shaderLightens = 0;

    /**
     * Whether a pack's GUI text shader repaints vanilla's exact label grey in a light colour,
     * as a dark-mode pack's does: its {@code text.fsh} or {@code text.vsh} names 0x404040 and
     * a light colour to paint it with ({@link #repaintsLight}). Read once per reload.
     */
    public static boolean shaderLightens() {
        int known = shaderLightens;
        if (known != 0) return known == 2;
        StringBuilder src = new StringBuilder();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            for (Identifier id : TEXT_SHADER) {
                Optional<Resource> res = mc.getResourceManager().getResource(id);
                if (!PackArt.fromPack(res, PackArt.VANILLA_PACK)) continue;
                try (InputStream in = res.get().open()) {
                    src.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                } catch (Exception e) {
                    LOG.warn("Couldn't read {}: {}", id, e.toString());
                }
            }
        }
        boolean yes = repaintsLight(src.toString());
        shaderLightens = yes ? 2 : 1;
        return yes;
    }

    private static final Pattern HEX = Pattern.compile("0x([0-9a-f]{6})\\b");
    private static final Pattern VEC = Pattern.compile("vec[34]\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)");

    /**
     * Whether shader source repaints vanilla's grey light, pure: it names 0x404040 (or its
     * decimal 4210752), and a light colour besides it, as a hex literal or a {@code vec3} of
     * 0 to 1. A shader that names the grey only to paint it dark, or to test it, is no repaint
     * MenuKit can leave its text to.
     */
    // ponytail: a text match on the shader's source; a pack that spells either colour another way reads as "no", and MenuKit lightens its text itself.
    public static boolean repaintsLight(String source) {
        String src = source.toLowerCase(Locale.ROOT);
        if (!src.contains("0x404040") && !src.contains("4210752")) return false;
        Matcher hex = HEX.matcher(src);
        while (hex.find()) {
            int c = 0xFF000000 | Integer.parseInt(hex.group(1), 16);
            if (c != TEXT && !darkFill(c)) return true;
        }
        Matcher vec = VEC.matcher(src);
        while (vec.find()) {
            try {
                int c = 0xFF000000;
                for (int i = 0; i < 3; i++) {
                    double v = Double.parseDouble(vec.group(i + 1));
                    if (v < 0 || v > 1) { c = TEXT; break; }
                    c |= (int) Math.round(v * 255) << (16 - 8 * i);
                }
                if (c != TEXT && !darkFill(c)) return true;
            } catch (NumberFormatException ignored) {
                // not a colour
            }
        }
        return false;
    }

    // The colour text is drawn in, on the surface under it

    /** A memo key: text in {@code colour} (muted or not) on {@code fill}. */
    record Key(int colour, boolean muted, int fill) {}

    // ponytail: one memo of each fill's text colours, dropped on a reload; a screen shows a handful of fills.
    private static final Map<Key, Integer> MEMO = new ConcurrentHashMap<>();

    /**
     * The colour text in a role is drawn in on a surface of {@code look} ({@code colour},
     * the role's own: the Panel look's body grey, or its muted grey when {@code muted}): one
     * that reads on the surface's fill ({@link #textOn}, {@link #mutedOn}).
     */
    public static int on(PackLook.Look look, int colour, boolean muted) {
        int fill = look.fill();
        return MEMO.computeIfAbsent(new Key(colour, muted, fill),
                k -> muted ? mutedOn(fill) : textOn(colour, fill, shaderLightens()));
    }
}
