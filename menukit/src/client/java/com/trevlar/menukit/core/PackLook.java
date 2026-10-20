package com.trevlar.menukit.core;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panel-look panel surfaces that follow resource packs (spec "Panel-look panels follow
 * resource packs", 2026-10-20). Packs restyle containers by replacing whole
 * {@code textures/gui/container/*.png} files with the slots drawn into the art; none add
 * nine-slice data. So a panel surface's look is <b>cut from the container texture</b> the
 * active pack supplies: a frame, a fill and a slot.
 *
 * <h3>Where a look comes from (spec part 1), per texture</h3>
 * <ol>
 *   <li>A pack overrides {@code menukit:panel_raised}: that sprite. The pack themed MenuKit
 *       directly, so it wins over anything cut from a container texture.</li>
 *   <li>The container texture is vanilla's own: MenuKit's sprite exactly as before, so an
 *       unmodded game looks unchanged.</li>
 *   <li>Otherwise the look is cut from the pack's texture, unless its fill is not one colour
 *       or no clean strip of edge is found: then MenuKit's sprite again ({@link Source#FALLBACK}).</li>
 * </ol>
 *
 * <h3>What is cut (spec parts 2 and 3)</h3>
 * A container texture has two <b>parts</b>: the container's (top) and the player
 * inventory's (vanilla's last 96 rows). Some packs draw the inventory as a box of its own
 * with its own top border (Recolourful, Touched UI); then the container part has no bottom
 * border, and its top border is mirrored for one. From a part:
 * <ul>
 *   <li><b>corners</b>, 7 px (vanilla's slots start at x 7), grown at the top while an
 *       ornament runs past them (Touched UI), never into the slots;</li>
 *   <li><b>edges</b>, tiled along each side from the longest strip clear of corners, slots
 *       and decoration (Recolourful's latch is skipped), never stretched;</li>
 *   <li><b>the fill</b>: the colour of the longest run of flat rows in the part's slot-free
 *       strips (the title strip, the gap above the hotbar);</li>
 *   <li><b>a slot</b>, at vanilla's first slot position, if it matches its neighbours (else
 *       {@code container/slot} if a pack restyled it, else the fill-drawn slot).</li>
 * </ul>
 * Everything is in the texture's 256-unit coordinates and drawn as blits in them, so a
 * 512 px pack (Faithful 32x) needs nothing extra.
 *
 * <h3>Cached, never read per frame</h3>
 * A texture is read and cut once, on its first use after a resource reload; the cache is
 * dropped on every reload ({@link #onReload}). Drawing reads only the cut.
 */
@ApiStatus.Internal
public final class PackLook {

    private PackLook() {}

    private static final Logger LOG = LoggerFactory.getLogger("MenuKit/PackLook");

    /** Vanilla's chest texture: the look of a plain Panel-look panel (spec part 6). */
    public static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    /** Vanilla's shulker box texture. */
    public static final Identifier SHULKER_BOX = Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");

    /** MenuKit's raised panel sprite, and its file a pack overrides to theme MenuKit directly. */
    public static final Identifier PANEL_SPRITE = Identifier.fromNamespaceAndPath("menukit", "panel_raised");
    private static final Identifier PANEL_SPRITE_FILE =
            Identifier.fromNamespaceAndPath("menukit", "textures/gui/sprites/panel_raised.png");
    /** Vanilla's slot sprite (horse, nautilus, stats), the slot fallback when a pack restyled it. */
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier SLOT_SPRITE_FILE =
            Identifier.withDefaultNamespace("textures/gui/sprites/container/slot.png");

    /** The pack ids of vanilla's own resources and of MenuKit's own (a Fabric mod's pack is its mod id). */
    static final String VANILLA_PACK = "vanilla", MENUKIT_PACK = "menukit";

    /** MenuKit's sprite's fill, the vanilla grey. */
    private static final int SPRITE_FILL = 0xFFC6C6C6;
    /** MenuKit's sprite's frame: its outline and its two-pixel bevel, on every side, before the fill. */
    private static final int[] SPRITE_FRAME = {3, 3, 3, 3};

    // ---------------------------------------------------------------------------------
    // Comparison switches for the review shots. Each holds the variant the spec chose by
    // default; flipping one shows the other. Delete with the losing code once picked.
    // ---------------------------------------------------------------------------------

    /** mockup: the review shots' variant switches (Trev picks; then delete this class and its uses). */
    public static final class Mockup {
        private Mockup() {}
        /** mockup: edges and fill stretched over the side instead of tiled from clean strips. */
        public static volatile boolean stretch = false;
        /** mockup: draw the cut even where it falls back (a non-uniform fill), to show why it does. */
        public static volatile boolean noFallback = false;
        /** mockup: where two region sections meet, each closes its own inner ring ({@link #drawCap}). */
        public static volatile boolean capped = false;
    }

    // The analysis

    /** Which part of a container texture a surface is cut from. */
    public enum Part {
        /** The container's own part, above the inventory: a container section, a plain panel. */
        CONTAINER,
        /** The player inventory's part, vanilla's last 96 rows: the region's inventory panel. */
        INVENTORY
    }

    /** Where a look comes from (spec part 1). */
    public enum Source {
        /** The texture is vanilla's own: MenuKit's sprite, as before packs were followed. */
        VANILLA,
        /** A pack overrides {@code menukit:panel_raised}: that sprite. */
        OVERRIDE,
        /** Cut from the pack's texture. */
        PACK,
        /** The pack's art can't be cut cleanly (a textured fill): MenuKit's sprite. */
        FALLBACK
    }

    /**
     * What was cut from one part of a texture, all in the texture's 256-unit coordinates.
     *
     * @param x0 the part's left edge (first opaque column)        @param x1 its right edge (exclusive)
     * @param top the first row of its top border                   @param bottom the last row of its bottom border (exclusive)
     * @param mirror whether the bottom border is the top one mirrored (the part has none of its own)
     * @param l left border width   @param r right border width   @param t top border height   @param b bottom border height
     * @param sTop the top corners' size (square)                   @param sBot the bottom corners' size (square)
     * @param fill the fill colour (ARGB)                           @param uniform whether a flat run of rows was found
     * @param topU top edge strip's first column, {@code topW} its width; likewise bottom, left and right ({@code V}, {@code H})
     * @param sideFrom the first row the side strips may come from, {@code sideTo} the last (exclusive)
     * @param sample the slot-free strip the fill is sampled from, {@code [x0, y0, x1, y1]}: tiled when the fill is textured
     * @param slotV the row of the slot cut (its column is vanilla's 7)
     * @param slotOk whether the slot cut matches its neighbours
     * @param clean whether the part is clean enough to draw: a uniform fill and every edge strip found
     * @param insetL how many pixels in from the left the texture's own top corner starts, per row from its top
     *               (its rounding); {@code insetR} from the right. A top corner is drawn in that shape, so one cut
     *               from a box drawn under another (Recolourful's inventory) leaves out the art of the box above
     * @param texW the texture's width in units, {@code texH} its height (for normalising UVs)
     * @param frame how far in from each side the frame's art ends and the fill begins, {@code [left, top, right, bottom]}
     * @param ring the frame's inner ring on the left and the right, just inside its outline, next to the fill:
     *             {@code [left depth, left colour, right depth, right colour]} (depth 0: none)
     */
    public record Cut(int x0, int x1, int top, int bottom, boolean mirror, int l, int r, int t, int b,
                      int sTop, int sBot, int fill, boolean uniform,
                      int topU, int topW, int botU, int botW, int leftV, int leftH, int rightV, int rightH,
                      int sideFrom, int sideTo, int[] sample, int slotV, boolean slotOk, boolean clean, int[] insetL, int[] insetR,
                      int texW, int texH, int[] frame, int[] ring) {}

    /**
     * A surface's look: where it comes from, the cut (kept even when it falls back, for the
     * comparison shots), its fill, and whether vanilla's slot sprite was restyled by a pack.
     * {@code spriteFrame} is the frame's inner edge where the surface is MenuKit's sprite or
     * a pack's override of it ({@link #frame}).
     */
    public record Look(Identifier texture, Part part, Source source, @Nullable Cut cut, int spriteFill,
                       boolean slotSprite, int[] spriteFrame) {

        /**
         * How far in from each side of a surface in this look its frame ends,
         * {@code [left, top, right, bottom]} in GUI pixels: where its fill starts. A block
         * laid inside the surface (a container region section in its own texture's fill) goes this far out,
         * so it meets the frame and leaves the frame itself as it is.
         */
        public int[] frame() {
            return drawsCut() ? cut.frame() : spriteFrame;
        }

        /** Whether this surface draws the cut (else MenuKit's sprite, or the pack's override of it). */
        public boolean drawsCut() {
            return cut != null && (source == Source.PACK || (source == Source.FALLBACK && Mockup.noFallback));
        }

        /** The surface's fill colour: what text on it is read against, and a block of it inside another surface (a section in the region). */
        public int fill() {
            return drawsCut() ? cut.fill() : spriteFill;
        }

        /** How far the look's top corners reach in and down, in GUI pixels: past its ornaments (Touched UI's brackets). */
        public int corner() {
            return drawsCut() ? cut.sTop() : 0;
        }
    }

    // ponytail: one cache entry per texture and part, dropped whole on a resource reload.
    private static final Map<String, Look> CACHE = new ConcurrentHashMap<>();

    /** A resource reload: every look is cut again from what the new packs supply, on its next use. */
    public static void onReload() {
        CACHE.clear();
        OptiGuiRules.onReload();
        ControlLook.onReload();
        TEXT_MEMO.clear();
        shaderLightens = 0;
    }

    /** The look of {@code part} of {@code texture} under the active packs, cut on first use after a reload. */
    public static Look look(Identifier texture, Part part) {
        return CACHE.computeIfAbsent(texture + "#" + part, k -> resolve(texture, part));
    }

    // Text that reads on the fill (spec part 8)

    /** The Panel look's heading and body text, vanilla's label grey ({@code ElementConstants.TEXT_DARK}). */
    private static final int TEXT = 0xFF404040;
    /** MenuKit's muted text ({@code ElementConstants.TEXT_MUTED}). */
    private static final int MUTED = 0xFF8B8B8B;
    private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;

    /**
     * The look of a plain Panel-look panel (a settings screen): the chest's container part
     * (spec part 6), whatever its fill. A dark chest (Touched UI's) keeps its look; its text
     * turns light instead ({@link #textOn}).
     */
    public static Look panel() {
        return look(CHEST, Part.CONTAINER);
    }

    /** Below this contrast with its fill, body text doesn't read: WCAG's 3:1, its floor for large text. */
    public static final double BODY_MIN = 3.0;
    /** How far body text that has to change stands out from its fill: WCAG's 4.5:1 for text. */
    public static final double BODY_TARGET = 4.5;
    /** How far muted text stands out from MenuKit's own fill: what muted text keeps on any fill. */
    private static final double MUTED_CONTRAST = contrast(MUTED, SPRITE_FILL);

    /**
     * Heading and body text {@code text} on {@code fill}, pure. {@code text} itself wherever
     * it reads ({@link #BODY_MIN}), so vanilla's grey stays on MenuKit's own fill and on any
     * light one. On a fill too dark or too coloured for it, a colour made from the fill: the
     * fill taken toward white (on a dark fill, Touched UI's chest) or black (on a light one,
     * Recolourful's orange) until it stands out by {@link #BODY_TARGET}, so the text keeps
     * the fill's hue.
     *
     * <p>{@code shaderLightens} says the pack's text shader repaints vanilla's exact grey in
     * a light colour (Default Dark Mode's does). Then on a dark fill vanilla's grey is left as
     * it is, for the pack to repaint as it does on its own screens: lightened once, by the pack.
     */
    public static int textOn(int text, int fill, boolean shaderLightens) {
        if (contrast(text, fill) >= BODY_MIN) return text;
        boolean dark = darkFill(fill);
        if (dark && shaderLightens && text == TEXT) return text;
        return standingOut(fill, dark ? WHITE : BLACK, BODY_TARGET);
    }

    /**
     * Muted text on {@code fill}, pure: MenuKit's muted grey wherever it stands out from the
     * fill as much as it does from MenuKit's own fill (so exactly as before with no pack, and
     * on Default Dark Mode's dark grey), else the fill taken toward white or black until it
     * does: between the fill and the body text, in the fill's hue (Recolourful's orange).
     */
    public static int mutedOn(int fill) {
        if (contrast(MUTED, fill) >= MUTED_CONTRAST) return MUTED;
        return standingOut(fill, darkFill(fill) ? WHITE : BLACK, MUTED_CONTRAST);
    }

    /** Whether text reads better light than dark on {@code fill}. */
    static boolean darkFill(int fill) {
        return contrast(fill, WHITE) > contrast(fill, BLACK);
    }

    /** The GUI text shader, which a pack replaces to repaint vanilla's text (Default Dark Mode). */
    private static final Identifier TEXT_SHADER = Identifier.withDefaultNamespace("shaders/core/text.fsh");
    /** {@link #shaderLightens}, read once per reload: {@code 0} unread, {@code 1} no, {@code 2} yes. */
    private static volatile int shaderLightens = 0;

    /**
     * Whether a pack's GUI text shader repaints vanilla's exact label grey: its {@code text.fsh}
     * names 0x404040 (Default Dark Mode's turns it 0xAAAAAA). Read once per reload.
     */
    // ponytail: a text match on the shader's source; a pack that spells the grey another way reads as "no", and its text is lightened by MenuKit instead.
    public static boolean shaderLightens() {
        int known = shaderLightens;
        if (known != 0) return known == 2;
        boolean yes = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            Optional<Resource> res = mc.getResourceManager().getResource(TEXT_SHADER);
            if (fromPack(res, VANILLA_PACK)) {
                try (InputStream in = res.get().open()) {
                    String src = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                    yes = src.contains("0x404040") || src.contains("4210752");
                } catch (Exception e) {
                    LOG.warn("Couldn't read {}: {}", TEXT_SHADER, e.toString());
                }
            }
        }
        shaderLightens = yes ? 2 : 1;
        return yes;
    }

    // ponytail: one memo of each fill's text colours, dropped on a reload; a screen shows a handful of fills.
    private static final Map<Long, Integer> TEXT_MEMO = new ConcurrentHashMap<>();

    /**
     * The colour text in a role is drawn in at {@code (x, y)} now ({@code colour}, the role's
     * own: the Panel look's body grey, or its muted grey when {@code muted}). On a panel
     * surface, one that reads on that surface's fill ({@link #textOn}, {@link #mutedOn}: in
     * the container region, the fill of the section under it); anywhere else (no surface, a
     * card drawn over one) {@code colour} as given.
     */
    public static int textAt(int colour, boolean muted, int x, int y) {
        Look look = slotLookAt(x, y);
        if (look == null) return colour;
        int fill = look.fill();
        long key = ((long) fill << 32) | ((colour & 0xFFFFFFFFL) ^ (muted ? 1L << 31 : 0));
        return TEXT_MEMO.computeIfAbsent(key, k -> muted ? mutedOn(fill) : textOn(colour, fill, shaderLightens()));
    }

    /** WCAG's contrast ratio of two colours, 1 to 21. */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
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

    private static double luminance(int argb) {
        double r = channel((argb >> 16) & 0xFF), g = channel((argb >> 8) & 0xFF), b = channel(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Works out one look (spec part 1's order). Never throws: anything unreadable is MenuKit's sprite. */
    private static Look resolve(Identifier texture, Part part) {
        // No game (MenuKit's headless checks draw panels too): MenuKit's own sprite.
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return new Look(texture, part, Source.VANILLA, null, SPRITE_FILL, false, SPRITE_FRAME);
        ResourceManager rm = mc.getResourceManager();
        boolean slotSprite = fromPack(rm.getResource(SLOT_SPRITE_FILE), VANILLA_PACK);

        // 1. A pack restyled MenuKit's own sprite: that, whatever the container texture.
        Optional<Resource> sprite = rm.getResource(PANEL_SPRITE_FILE);
        if (fromPack(sprite, MENUKIT_PACK)) {
            int centre = SPRITE_FILL;
            int[] frame = SPRITE_FRAME;
            try (InputStream in = sprite.get().open(); NativeImage img = NativeImage.read(in)) {
                centre = img.getPixel(img.getWidth() / 2, img.getHeight() / 2);
                frame = spriteFrame(img, centre);
            } catch (Exception e) {
                LOG.warn("Couldn't read the overridden {}: {}", PANEL_SPRITE_FILE, e.toString());
            }
            return new Look(texture, part, Source.OVERRIDE, null, centre, slotSprite, frame);
        }

        // 2. Vanilla's own texture (or none at all): MenuKit's sprite, unchanged.
        Optional<Resource> res = rm.getResource(texture);
        if (!fromPack(res, VANILLA_PACK)) return new Look(texture, part, Source.VANILLA, null, SPRITE_FILL, slotSprite, SPRITE_FRAME);

        // 3. Cut from the pack's texture, or MenuKit's sprite when it can't be cut cleanly.
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            Cut cut = analyse(new Pixels(img), part == Part.INVENTORY);
            Source source = cut != null && cut.clean() ? Source.PACK : Source.FALLBACK;
            LOG.debug("{} {} from {}: {}", texture, part, res.get().sourcePackId(), source);
            return new Look(texture, part, source, cut, SPRITE_FILL, slotSprite, SPRITE_FRAME);
        } catch (Exception e) {
            LOG.warn("Couldn't cut a panel look from {}: {}", texture, e.toString());
            return new Look(texture, part, Source.FALLBACK, null, SPRITE_FILL, slotSprite, SPRITE_FRAME);
        }
    }

    /**
     * A pack's own {@code menukit:panel_raised}'s frame: on each side, across its middle, how
     * far in its last pixel unlike its centre is, in the sprite's 9 px of GUI.
     */
    // ponytail: assumes MenuKit's nine-slice size (9 px); a pack that also replaces the .mcmeta with another size reads off.
    private static int[] spriteFrame(NativeImage img, int centre) {
        int w = img.getWidth(), h = img.getHeight();
        int[] across = new int[w], down = new int[h];
        for (int x = 0; x < w; x++) across[x] = img.getPixel(x, h / 2);
        for (int y = 0; y < h; y++) down[y] = img.getPixel(w / 2, y);
        int[] f = {inset(Arrays.copyOfRange(across, 0, w / 2), centre, true), inset(Arrays.copyOfRange(down, 0, h / 2), centre, true),
                inset(Arrays.copyOfRange(across, w - w / 2, w), centre, false), inset(Arrays.copyOfRange(down, h - h / 2, h), centre, false)};
        for (int i = 0; i < 4; i++) f[i] = Math.round(f[i] * 9f / (i % 2 == 0 ? w : h));
        return f;
    }

    /**
     * How deep a border profile's frame is: from its outer end to just past the last pixel
     * unlike {@code fill}. {@code outerFirst} says which end of {@code profile} is the outside.
     */
    static int inset(int[] profile, int fill, boolean outerFirst) {
        int n = profile.length;
        for (int k = n - 1; k >= 0; k--) {
            int i = outerFirst ? k : n - 1 - k;   // the k-th pixel in from the outside
            if (dist(profile[i], fill) > TOL) return k + 1;
        }
        return 0;
    }

    /** Whether a resource is present and supplied by a pack other than {@code own}, its owner's. */
    static boolean fromPack(Optional<Resource> res, String own) {
        return res.isPresent() && !own.equals(res.get().sourcePackId());
    }

    // The cut, pure over pixels: a port of the prototype the research shots were checked with.

    /** The width of a corner and border: vanilla's slots start at x 7. */
    private static final int C = 7;
    /** How far apart two pixels may be, per channel, and still count as the same art. */
    private static final int TOL = 8;
    /** How far apart two pixels may be, per channel, in a flat fill row (pixel-art fills are exact). */
    private static final int FLAT = 3;
    /** Vanilla's slot grid: from x 7, nine 18 px slots wide. */
    private static final int GRID_X0 = 7, GRID_X1 = 7 + 9 * 18;
    /** The rows vanilla gives the player's inventory part, at the bottom of a container texture. */
    private static final int INVENTORY_PART = 96;

    /** A texture's pixels, read in its 256-unit coordinates (one sample per unit). */
    static final class Pixels {
        final int k, w, h;
        private final int[] argb;
        private final int nativeW;

        Pixels(NativeImage img) {
            this.nativeW = img.getWidth();
            this.k = Math.max(1, img.getWidth() / 256);
            this.w = img.getWidth() / k;
            this.h = img.getHeight() / k;
            this.argb = new int[img.getWidth() * img.getHeight()];
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) argb[x + y * nativeW] = img.getPixel(x, y);
        }

        /** For the self-check: pixels given in units directly. */
        Pixels(int w, int h, int[] argb) {
            this.k = 1; this.w = w; this.h = h; this.nativeW = w; this.argb = argb;
        }

        int at(int x, int y) {
            if (x < 0 || y < 0 || x >= w || y >= h) return 0;
            return argb[x * k + y * k * nativeW];
        }

        boolean opaque(int x, int y) {
            return (at(x, y) >>> 24) != 0;
        }
    }

    /** The largest per-channel difference between two ARGB colours, alpha included. */
    static int dist(int a, int b) {
        int d = 0;
        for (int s = 0; s < 32; s += 8) d = Math.max(d, Math.abs(((a >>> s) & 0xFF) - ((b >>> s) & 0xFF)));
        return d;
    }

    private static boolean same(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (dist(a[i], b[i]) > TOL) return false;
        return true;
    }

    /** The most common of {@code items} (exact): an edge's usual profile, a row's usual colour. */
    private static int[] modal(List<int[]> items) {
        Map<List<Integer>, Integer> counts = new HashMap<>();
        int[] best = items.isEmpty() ? new int[0] : items.get(0);
        int bestN = 0;
        for (int[] it : items) {
            int n = counts.merge(Arrays.stream(it).boxed().toList(), 1, Integer::sum);
            if (n > bestN) { bestN = n; best = it; }
        }
        return best;
    }

    /**
     * Cuts one part of a container texture. {@code null} when it isn't laid out like one
     * (too short for vanilla's inventory part, or nothing opaque where the frame should be).
     */
    static @Nullable Cut analyse(Pixels p, boolean inventory) {
        // The image's extent down its frame: vanilla's chest is 222 rows, the shulker box's 166.
        int bottom = -1, firstY = -1;
        for (int y = 0; y < p.h; y++) if (p.opaque(40, y)) { if (firstY < 0) firstY = y; bottom = y + 1; }
        if (bottom < 0 || bottom - INVENTORY_PART < 18) return null;
        int vanillaSplit = bottom - INVENTORY_PART;

        // A pack that draws the inventory as a box of its own starts its top border near
        // vanilla's split, in the outer border's colour (Recolourful, Touched UI at row 132).
        int outer = p.at(10, firstY);
        int own = -1;
        for (int y = vanillaSplit; y < vanillaSplit + 14 && y < bottom; y++) {
            if (dist(p.at(10, y), outer) <= TOL && dist(p.at(10, y - 1), outer) > TOL) { own = y; break; }
        }
        int split = own >= 0 ? own : vanillaSplit;

        // This part's rows. The inventory takes the texture's top border when it has none of
        // its own (the frame runs on unbroken); the container part mirrors its top border
        // when the inventory box starts right under it, so it gets a bottom border too.
        int top, p0, p1;
        boolean mirror;
        if (inventory) { top = own >= 0 ? own : firstY; p0 = split; p1 = bottom; mirror = false; }
        else { top = firstY; p0 = firstY; p1 = split; mirror = own >= 0; }
        int mid = (p0 + p1) / 2;
        int x0 = -1, x1 = -1;
        for (int x = 0; x < p.w; x++) if (p.opaque(x, mid)) { if (x0 < 0) x0 = x; x1 = x + 1; }
        if (x0 < 0) return null;

        // Border widths: 7, or less where the art starts right of 0 (Touched UI), so a
        // border never takes in the slots at x 7.
        int l = Math.max(2, Math.min(C, GRID_X0 - x0)), r = Math.max(2, Math.min(C, x1 - GRID_X1)), t = C, b = C;
        int contEnd = 17 + 18 * ((split - 17) / 18);
        final int bot = bottom;
        java.util.function.BiPredicate<Integer, Integer> slot = (x, y) -> x >= GRID_X0 && x < GRID_X1
                && ((y >= bot - 83 && y < bot - 29) || (y >= bot - 25 && y < bot - 7) || (y >= 17 && y < contEnd));

        // The fill: the colour of the longest run of flat rows in the part's slot-free
        // strips. Wood grain (Wilder Forest) has no flat run; a zigzag along the title
        // (Recolourful's shulker box) is skipped for the plain rows under it.
        List<Integer> rows = new ArrayList<>();
        if (inventory) {
            for (int y = p0 + t; y < bottom - 83; y++) rows.add(y);
            for (int y = bottom - 29; y < bottom - 25; y++) rows.add(y);
        } else {
            for (int y = top + t; y < 17; y++) rows.add(y);
        }
        int fill = SPRITE_FILL, run = 0, bestRun = 0, cur = 0;
        boolean inRun = false;
        for (int y : rows) {
            List<int[]> cs = new ArrayList<>();
            for (int x = x0 + l; x < x1 - r; x++) cs.add(new int[]{p.at(x, y)});
            int m = modal(cs)[0];
            int near = 0;
            for (int[] c : cs) if (dist(c[0], m) <= FLAT) near++;
            boolean flat = near >= 0.9 * cs.size();
            if (flat && inRun && dist(m, cur) <= FLAT) run++;
            else if (flat) { cur = m; run = 1; inRun = true; }
            else { inRun = false; run = 0; }
            if (run > bestRun) { bestRun = run; fill = cur; }
        }
        boolean uniform = bestRun >= 3;
        if (!uniform && !rows.isEmpty()) fill = p.at((x0 + x1) / 2, rows.get(rows.size() / 2));
        // Where a textured fill is tiled from (only when the cut is drawn anyway, for the shots).
        int[] sample = inventory
                ? new int[]{x0 + l, bottom - 29, x1 - r, bottom - 25}
                : new int[]{x0 + l, top + t, x1 - r, Math.max(top + t + 1, 17)};

        // Each side's usual profile across its border.
        List<int[]> lefts = new ArrayList<>(), rights = new ArrayList<>(), tops = new ArrayList<>(), bots = new ArrayList<>();
        int innerFrom = top + t, innerTo = inventory ? bottom - b : p1;
        for (int y = innerFrom; y < innerTo; y++) { lefts.add(rowOf(p, y, x0, l)); rights.add(rowOf(p, y, x1 - r, r)); }
        for (int x = x0 + l; x < x1 - r; x++) { tops.add(colOf(p, x, top, t)); if (!mirror) bots.add(colOf(p, x, bottom - b, b)); }
        if (lefts.isEmpty() || tops.isEmpty()) return null;
        int[] leftM = modal(lefts), rightM = modal(rights), topM = modal(tops), botM = mirror ? null : modal(bots);

        // Top corners grow while an ornament runs past them (Touched UI's brackets), as
        // squares, never into the slots: one that would is left at its border's size.
        int sTop = Math.max(t, Math.max(l, r));
        if (uniform) {
            sTop = Math.max(grow(p, false, x0, x1, top, l, r, t, leftM, rightM, topM, fill, slot),
                    grow(p, true, x0, x1, top, l, r, t, leftM, rightM, topM, fill, slot));
        }
        int sBot = mirror ? sTop : Math.max(b, Math.max(l, r));

        // Edge strips: the longest run along each side like its usual profile, clear of the
        // corners and of decoration (a latch, a hinge).
        int[] topRun = run(p, x0 + sTop, x1 - sTop, x -> colOf(p, x, top, t), topM);
        int[] botRun = mirror ? new int[]{topRun[0], topRun[1]}
                : run(p, x0 + sBot, x1 - sBot, x -> colOf(p, x, bot - b, b), botM);
        int sideFrom = top + sTop, sideTo = mirror || !inventory ? p1 : bottom - sBot;
        final int fx0 = x0, fx1 = x1;
        int[] leftRun = run(p, sideFrom, sideTo, y -> rowOf(p, y, fx0, l), leftM);
        int[] rightRun = run(p, sideFrom, sideTo, y -> rowOf(p, y, fx1 - r, r), rightM);

        // The slot: vanilla's first, kept if it matches the one beside it and the one below.
        int slotV = inventory ? bottom - 83 : 17;
        boolean slotOk = same(block(p, GRID_X0, slotV), block(p, GRID_X0 + 18, slotV))
                && same(block(p, GRID_X0, slotV), block(p, GRID_X0, slotV + 18));

        // The shape of the texture's own top corners (vanilla's are rounded: two pixels in
        // on the top row, one on the next), from the container part's outline.
        int cMid = (firstY + split) / 2, cx0 = -1, cx1 = -1;
        for (int x = 0; x < p.w; x++) if (p.opaque(x, cMid)) { if (cx0 < 0) cx0 = x; cx1 = x + 1; }
        int[] insetL = new int[sTop], insetR = new int[sTop];
        for (int j = 0; j < sTop; j++) {
            while (insetL[j] < sTop && !p.opaque(cx0 + insetL[j], firstY + j)) insetL[j]++;
            while (insetR[j] < sTop && !p.opaque(cx1 - 1 - insetR[j], firstY + j)) insetR[j]++;
        }

        // The frame's inner edge on each side, from its usual profile: where the fill starts.
        // The right and bottom profiles run from the inside out; a mirrored bottom is the top's.
        int frameTop = inset(topM, fill, true);
        int[] frame = {inset(leftM, fill, true), frameTop, inset(rightM, fill, false),
                mirror ? frameTop : inset(botM, fill, false)};

        int[] ringL = ring(leftM, frame[0], true), ringR = ring(rightM, frame[2], false);

        boolean clean = uniform && Math.min(Math.min(topRun[1], botRun[1]), Math.min(leftRun[1], rightRun[1])) >= 2;
        return new Cut(x0, x1, top, bottom, mirror, l, r, t, b, sTop, sBot, fill, uniform,
                topRun[0], topRun[1], botRun[0], botRun[1], leftRun[0], leftRun[1], rightRun[0], rightRun[1],
                sideFrom, sideTo, sample, slotV, slotOk, clean, insetL, insetR, p.w, p.h, frame,
                new int[]{ringL[0], ringL[1], ringR[0], ringR[1]});
    }

    /**
     * A side's inner ring: the run of one colour at the frame's inner edge, {@code depth} in
     * from the outside, back toward the outline but never taking it in (the outermost pixel).
     * {@code [depth, colour]}; {@code [0, 0]} for a frame of the outline alone.
     */
    static int[] ring(int[] profile, int depth, boolean outerFirst) {
        int n = profile.length;
        if (depth < 2 || depth > n) return new int[]{0, 0};
        java.util.function.IntUnaryOperator at = k -> profile[outerFirst ? k : n - 1 - k];   // the k-th pixel in from the outside
        int colour = at.applyAsInt(depth - 1), count = 1;
        for (int k = depth - 2; k >= 1 && dist(at.applyAsInt(k), colour) <= TOL; k--) count++;
        return new int[]{count, colour};
    }

    private static int[] rowOf(Pixels p, int y, int x, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = p.at(x + i, y);
        return out;
    }

    private static int[] colOf(Pixels p, int x, int y, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = p.at(x, y + i);
        return out;
    }

    private static int[] block(Pixels p, int x, int y) {
        int[] out = new int[18 * 18];
        for (int j = 0; j < 18; j++) for (int i = 0; i < 18; i++) out[i + j * 18] = p.at(x + i, y + j);
        return out;
    }

    /** The longest run in {@code [from, to)} whose profile is like {@code ref}: {@code [start, length]}. */
    private static int[] run(Pixels p, int from, int to, java.util.function.IntFunction<int[]> profile, int[] ref) {
        int bestStart = from, best = 0, start = -1;
        for (int i = from; i <= to; i++) {
            boolean ok = i < to && same(profile.apply(i), ref);
            if (ok && start < 0) start = i;
            if (!ok && start >= 0) {
                if (i - start > best) { best = i - start; bestStart = start; }
                start = -1;
            }
        }
        return new int[]{bestStart, best};
    }

    /**
     * How big a top corner must be to hold its ornament: the smallest square from its
     * border's size whose next two shells (the row below it and the column beside it) are
     * plain (the side's border profile, the top's, the fill). One that would reach a slot
     * stays at the border's size.
     */
    private static int grow(Pixels p, boolean right, int x0, int x1, int top, int l, int r, int t,
                            int[] leftM, int[] rightM, int[] topM, int fill,
                            java.util.function.BiPredicate<Integer, Integer> slot) {
        int side = right ? r : l, base = Math.max(t, side);
        for (int s = base; s < 24; s++) {
            Boolean a = shell(p, right, x0, x1, top, side, t, leftM, rightM, topM, fill, slot, s);
            Boolean b2 = a == null ? null : shell(p, right, x0, x1, top, side, t, leftM, rightM, topM, fill, slot, s + 1);
            if (a == null || b2 == null) return base;
            if (a && b2) return s;
        }
        return base;
    }

    /** Whether shell {@code s} of a top corner is plain; {@code null} if it reaches a slot. */
    private static @Nullable Boolean shell(Pixels p, boolean right, int x0, int x1, int top, int side, int t,
                                           int[] leftM, int[] rightM, int[] topM, int fill,
                                           java.util.function.BiPredicate<Integer, Integer> slot, int s) {
        for (int i = 0; i <= s; i++) {
            int[][] cells = {{i, top + s}, {s, top + i}};
            for (int[] c : cells) {
                int off = c[0], y = c[1];
                int x = right ? x1 - 1 - off : x0 + off;
                if (slot.test(x, y)) return null;
                int expected;
                if (off < side) {
                    if (y - top < t) continue;  // the corner's own border square: the art itself
                    expected = right ? rightM[side - 1 - off] : leftM[off];
                } else if (y - top < t) expected = topM[y - top];
                else expected = fill;
                if (dist(p.at(x, y), expected) > TOL) return false;
            }
        }
        return true;
    }

    // Drawing

    /**
     * Draws a panel surface in {@code look}: MenuKit's sprite (or a pack's override of it),
     * or the frame and fill cut from the pack's texture.
     */
    public static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, Look look) {
        if (w <= 0 || h <= 0) return;
        if (!look.drawsCut()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE, x, y, w, h);
            return;
        }
        Cut c = look.cut();
        Identifier tex = look.texture();
        boolean stretch = Mockup.stretch;
        // Corners can't be bigger than half the panel.
        int sT = Math.min(c.sTop(), Math.min(w / 2, h / 2)), sB = Math.min(c.sBot(), Math.min(w / 2, h / 2));

        // The fill under everything, inside the borders.
        drawFill(g, x + c.l(), y + c.t(), w - c.l() - c.r(), h - c.t() - c.b(), look);

        // Edges: the top's strip along the top, the sides' down the sides, the bottom's (or
        // the top's mirrored) along the bottom; tiled, or stretched in the mockup variant
        // from the whole side between the corners, decoration and all.
        int topU = stretch ? c.x0() + c.sTop() : c.topU(), topW = stretch ? c.x1() - c.x0() - 2 * c.sTop() : c.topW();
        strip(g, c, tex, x + sT, y, w - 2 * sT, c.t(), topU, c.top(), topW, c.t(), false, stretch);
        int sideH = Math.max(1, c.sideTo() - c.sideFrom());
        int lv = stretch ? c.sideFrom() : c.leftV(), lh = stretch ? sideH : c.leftH();
        int rv = stretch ? c.sideFrom() : c.rightV(), rh = stretch ? sideH : c.rightH();
        strip(g, c, tex, x, y + sT, c.l(), h - sT - sB, c.x0(), lv, c.l(), lh, false, stretch);
        strip(g, c, tex, x + w - c.r(), y + sT, c.r(), h - sT - sB, c.x1() - c.r(), rv, c.r(), rh, false, stretch);
        if (c.mirror()) {
            strip(g, c, tex, x + sB, y + h - c.t(), w - 2 * sB, c.t(), topU, c.top(), topW, c.t(), true, stretch);
            corner(g, c, tex, x, y + h - sB, sB, c.x0(), c.top(), false, true);
            corner(g, c, tex, x + w - sB, y + h - sB, sB, c.x1() - sB, c.top(), true, true);
        } else {
            int botU = stretch ? c.x0() + c.sBot() : c.botU(), botW = stretch ? c.x1() - c.x0() - 2 * c.sBot() : c.botW();
            strip(g, c, tex, x + sB, y + h - c.b(), w - 2 * sB, c.b(), botU, c.bottom() - c.b(), botW, c.b(), false, stretch);
            piece(g, c, tex, x, y + h - sB, sB, sB, c.x0(), c.bottom() - sB, sB, sB, false);
            piece(g, c, tex, x + w - sB, y + h - sB, sB, sB, c.x1() - sB, c.bottom() - sB, sB, sB, false);
        }
        corner(g, c, tex, x, y, sT, c.x0(), c.top(), false, false);
        corner(g, c, tex, x + w - sT, y, sT, c.x1() - sT, c.top(), true, false);
    }

    /**
     * mockup: where two region sections meet, one closes its inner ring across the list
     * ({@link Mockup#capped}), {@code rows} deep at {@code y}, the list {@code w} wide at
     * {@code x}: a section's top in its left side's ring colour, running into that ring and up
     * to the right side's, its bottom in the right side's, running from the left side's ring
     * into the right's, as a bevel is lit. The outline, outside the rings, runs on. Nothing
     * for MenuKit's sprite or a ring-less frame.
     */
    public static void drawCap(GuiGraphicsExtractor g, Look look, int x, int w, int y, int rows, boolean top) {
        if (!look.drawsCut() || rows <= 0) return;
        Cut c = look.cut();
        int[] f = c.frame(), ring = c.ring();
        int depth = top ? ring[0] : ring[2];
        if (depth <= 0) return;
        rows = Math.min(rows, depth);
        int x0 = top ? x + f[0] - ring[0] : x + f[0], x1 = top ? x + w - f[2] : x + w - f[2] + ring[2];
        g.fill(x0, y, x1, y + rows, top ? ring[1] : ring[3]);
    }

    /** How deep a section's ring closing at its top ({@code top}) or bottom is in {@code look}: 0 for MenuKit's sprite. */
    public static int capDepth(Look look, boolean top) {
        return look.drawsCut() ? look.cut().ring()[top ? 0 : 2] : 0;
    }

    /**
     * A top corner (or one mirrored to the bottom), {@code s} square, in the shape of the
     * texture's own top corner: each row starts as far in as that corner's rounding, so the
     * pixels outside it are never drawn. Rows with the same inset go in one blit.
     */
    private static void corner(GuiGraphicsExtractor g, Cut c, Identifier tex, int x, int y, int s,
                               int u, int v, boolean right, boolean flip) {
        int[] insets = right ? c.insetR() : c.insetL();
        for (int j = 0; j < s; ) {
            int in = j < insets.length ? Math.min(insets[j], s) : 0, k = j + 1;
            while (k < s && (k < insets.length ? Math.min(insets[k], s) : 0) == in) k++;
            int dx = right ? 0 : in, dy = flip ? s - k : j;
            piece(g, c, tex, x + dx, y + dy, s - in, k - j, u + dx, v + j, s - in, k - j, flip);
            j = k;
        }
    }

    /**
     * Fills a rectangle with {@code look}'s fill: one colour for a uniform fill (a tiled
     * flat strip is one colour anyway), the sample strip tiled (or stretched) for a textured
     * one, which is drawn only in the no-fallback comparison.
     */
    public static void drawFill(GuiGraphicsExtractor g, int x, int y, int w, int h, Look look) {
        if (w <= 0 || h <= 0) return;
        Cut c = look.cut();
        if (!look.drawsCut() || c.uniform()) {
            g.fill(x, y, x + w, y + h, look.fill());
            return;
        }
        int[] s = c.sample();
        strip(g, c, look.texture(), x, y, w, h, s[0], s[1], s[2] - s[0], s[3] - s[1], false, Mockup.stretch);
    }

    /**
     * An 18x18 slot frame in {@code look}: the slot cut from the pack's texture; else
     * vanilla's {@code container/slot} sprite if a pack restyled it; else the fill-drawn slot.
     */
    public static void drawSlot(GuiGraphicsExtractor g, int x, int y, Look look) {
        Cut c = look.cut();
        if (look.drawsCut() && c.slotOk()) {
            piece(g, c, look.texture(), x, y, 18, 18, GRID_X0, c.slotV(), 18, 18, false);
        } else if (look.slotSprite()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, 18, 18);
        } else {
            PanelRendering.renderSlotBackground(g, x, y);
        }
    }

    /**
     * {@code (w x h)} at {@code (x, y)} from the texture's units {@code (u, v, uw, vh)}, mirrored top to bottom if asked.
     * Drawn with the float-UV {@code blit}, which OptiGUI's whole-screen texture swap does not hook (it hooks the
     * 13-argument one), so a piece is always of the texture the look was cut from: a lime box's screen never turns
     * another section's art lime. Keep every piece on this overload.
     */
    private static void piece(GuiGraphicsExtractor g, Cut c, Identifier tex, int x, int y, int w, int h,
                              int u, int v, int uw, int vh, boolean flip) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
        float u0 = u / (float) c.texW(), u1 = (u + uw) / (float) c.texW();
        float v0 = v / (float) c.texH(), v1 = (v + vh) / (float) c.texH();
        if (flip) { float tmp = v0; v0 = v1; v1 = tmp; }
        g.blit(tex, x, y, x + w, y + h, u0, u1, v0, v1);
    }

    /**
     * Covers {@code (w x h)} with the strip {@code (u, v, uw, vh)}: repeated at its own size,
     * the last copy cut short, or (the mockup variant) stretched once over the whole.
     */
    private static void strip(GuiGraphicsExtractor g, Cut c, Identifier tex, int x, int y, int w, int h,
                              int u, int v, int uw, int vh, boolean flip, boolean stretch) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
        if (stretch) {
            piece(g, c, tex, x, y, w, h, u, v, uw, vh, flip);   // mockup: stretched
            return;
        }
        for (int dy = 0; dy < h; dy += vh) {
            int ph = Math.min(vh, h - dy);
            // A cut-short copy of a mirrored strip takes its rows from the far end, so it
            // still meets the bottom edge the way the whole strip would.
            int pv = flip ? v + vh - ph : v;
            for (int dx = 0; dx < w; dx += uw) {
                int pw = Math.min(uw, w - dx);
                piece(g, c, tex, x + dx, y + dy, pw, ph, u, pv, pw, ph, flip);
            }
        }
    }

    // Which look a slot frame and text are drawn in

    /**
     * What a panel surface is at a screen point, set while it renders: the look a slot frame
     * drawn there takes, and whose fill text there is read against ({@link #textAt}).
     * {@code null} where there is no surface (a card drawn over one).
     */
    @FunctionalInterface
    public interface SlotLooks {
        @Nullable Look at(int x, int y);
    }

    private static @Nullable SlotLooks slotLooks;

    /**
     * While a panel renders, the look its slot frames and text take (the panel's own; per
     * section in the container region; {@code null} inside a card drawn over it). Returns
     * what was set before, for {@link #endSlots}. Render thread.
     */
    public static @Nullable SlotLooks beginSlots(@Nullable SlotLooks looks) {
        SlotLooks before = slotLooks;
        slotLooks = looks;
        return before;
    }

    /** Puts back what {@link #beginSlots} replaced. */
    public static void endSlots(@Nullable SlotLooks before) {
        slotLooks = before;
    }

    /** The look of a slot frame drawn at {@code (x, y)} now, or {@code null} outside any panel surface. */
    public static @Nullable Look slotLookAt(int x, int y) {
        SlotLooks s = slotLooks;
        return s == null ? null : s.at(x, y);
    }
}
