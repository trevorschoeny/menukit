package com.trevlar.menukit.core;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
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
import java.util.function.BiPredicate;
import java.util.function.IntFunction;

/**
 * Panel-look panel surfaces that follow resource packs. Packs restyle containers by replacing whole
 * {@code textures/gui/container/*.png} files with the slots drawn into the art; none add
 * nine-slice data. So a panel surface's look is <b>cut from the container texture</b> the
 * active pack supplies: a frame, a fill and a slot.
 *
 * <h3>Where a look comes from, per texture</h3>
 * <ol>
 *   <li>A pack overrides {@code menukit:panel_raised}: that sprite. The pack themed MenuKit
 *       directly, so it wins over anything cut from a container texture.</li>
 *   <li>The container texture is vanilla's own: MenuKit's sprite exactly as before, so an
 *       unmodded game looks unchanged.</li>
 *   <li>Otherwise the look is cut from the pack's texture, unless its fill is not one colour
 *       or no clean strip of edge is found: then MenuKit's sprite again ({@link Source#FALLBACK}).
 *       A textured fill (wood grain, say) can't be tiled from a strip without seams, and a
 *       frame cut from it would not match the pack's own screens anyway.</li>
 * </ol>
 *
 * <h3>What is cut</h3>
 * A container texture has two <b>parts</b>: the container's (top) and the player
 * inventory's (vanilla's last 96 rows). Some packs draw the inventory as a box of its own
 * with its own top border; then the container part has no bottom border, and its top
 * border is mirrored for one. From a part:
 * <ul>
 *   <li><b>corners</b>, 7 px (vanilla's slots start at x 7), grown at the top while an
 *       ornament runs past them, never into the slots;</li>
 *   <li><b>edges</b>, tiled along each side from the longest strip clear of corners, slots
 *       and decoration (a latch drawn on the frame is skipped), never stretched: stretching
 *       a side would smear its bevel and any pattern along it;</li>
 *   <li><b>the fill</b>: the colour of the longest run of flat rows in the part's slot-free
 *       strips (the title strip, the gap above the hotbar);</li>
 *   <li><b>a slot</b>, at vanilla's first slot position, if it matches its neighbours (else
 *       {@code container/slot} if a pack restyled it, else the fill-drawn slot).</li>
 * </ul>
 * An inventory part with no clean frame of its own (drawn in pieces: rows hung under the
 * container's box, the hotbar boxed apart) is drawn as the pack's plain panel ({@link #inventory}).
 * Everything is in the texture's 256-unit coordinates and drawn as blits in them, so a
 * 512 px (32x) pack, or a 128 px or 384 px one, needs nothing extra.
 *
 * <h3>Cached, never read per frame</h3>
 * A texture is read and cut once, on its first use after a resource reload; the cache is
 * dropped on every reload ({@link #onReload}). Drawing reads only the cut.
 */
@ApiStatus.Internal
public final class PackLook {

    private PackLook() {}

    private static final Logger LOG = LoggerFactory.getLogger("MenuKit/PackLook");

    /** Vanilla's chest texture: the look of a plain Panel-look panel, the most common container art a pack restyles. */
    public static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    /** Vanilla's shulker box texture. */
    public static final Identifier SHULKER_BOX = Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");

    /** MenuKit's raised panel sprite, which a pack overrides to theme MenuKit directly. */
    private static final Identifier PANEL_SPRITE = ControlLook.Control.PANEL.own(0);
    /** Vanilla's slot sprite, the slot fallback when a pack restyled it. */
    private static final Identifier SLOT_SPRITE = ControlLook.Control.SLOT.vanilla(0);

    /** MenuKit's sprite's fill, the vanilla grey. */
    static final int SPRITE_FILL = 0xFFC6C6C6;

    // The analysis

    /** Which part of a container texture a surface is cut from. */
    public enum Part {
        /** The container's own part, above the inventory: a container section, a plain panel. */
        CONTAINER,
        /** The player inventory's part, vanilla's last 96 rows: the region's inventory panel. */
        INVENTORY
    }

    /** Where a look comes from. */
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
     * @param fill the fill colour (ARGB)
     * @param edges the clean strip along each side the edges are tiled from
     * @param sideFrom the first row the side strips may come from, {@code sideTo} the last (exclusive)
     * @param slotV the row of the slot cut (its column is vanilla's 7)
     * @param slotOk whether the slot cut matches its neighbours
     * @param clean whether the part is clean enough to draw: a uniform fill and every edge strip found
     * @param insetL how many pixels in from the left the texture's own top corner starts, per row from its top
     *               (its rounding); {@code insetR} from the right. A top corner is drawn in that shape, so one cut
     *               from a box drawn under another (an inventory boxed apart) leaves out the art of the box above
     * @param texW the texture's width in units, {@code texH} its height (for normalising UVs)
     */
    public record Cut(int x0, int x1, int top, int bottom, boolean mirror, int l, int r, int t, int b,
                      int sTop, int sBot, int fill, Edges edges, int sideFrom, int sideTo, int slotV, boolean slotOk, boolean clean, int[] insetL, int[] insetR,
                      int texW, int texH) {}

    /**
     * A clean strip along one side of a part: from where (a column along the top and bottom,
     * a row down the sides) and how long.
     */
    public record Strip(int from, int length) {}

    /** The clean strip along each side of a part, tiled along that side ({@link #drawPanel}). */
    public record Edges(Strip top, Strip bottom, Strip left, Strip right) {}

    /**
     * A surface's look: where it comes from, the cut (kept when it falls back too, so a check
     * can see why it did), its fill, and whether vanilla's slot sprite was restyled by a pack.
     */
    public record Look(Identifier texture, Part part, Source source, @Nullable Cut cut, int spriteFill,
                       boolean slotSprite) {

        /** Whether this surface draws the cut (else MenuKit's sprite, or the pack's override of it). */
        public boolean drawsCut() {
            return cut != null && source == Source.PACK;
        }

        /** The surface's fill colour: what text on it is read against. */
        public int fill() {
            return drawsCut() ? cut.fill() : spriteFill;
        }

        /** How far the look's top corners reach in and down, in GUI pixels: past any ornament drawn over them. */
        public int corner() {
            return drawsCut() ? cut.sTop() : 0;
        }
    }

    /** A cache key: one part of one texture. */
    private record Key(Identifier texture, Part part) {}

    // ponytail: one cache entry per texture and part, dropped whole on a resource reload.
    private static final Map<Key, Look> CACHE = new ConcurrentHashMap<>();

    /** A resource reload: every look is cut again from what the new packs supply, on its next use. */
    public static void onReload() {
        CACHE.clear();
    }

    /** The look of {@code part} of {@code texture} under the active packs, cut on first use after a reload. */
    public static Look look(Identifier texture, Part part) {
        return CACHE.computeIfAbsent(new Key(texture, part), k -> resolve(texture, part));
    }

    /**
     * The look of a plain Panel-look panel (a settings screen): the chest's container part,
     * whatever its fill, so a settings screen matches the pack's most familiar container. A
     * dark chest keeps its look; its text turns light instead ({@link PackText#textOn}).
     */
    public static Look panel() {
        return look(CHEST, Part.CONTAINER);
    }

    /**
     * The look of the inventory panel on a screen drawn from {@code texture}: that texture's
     * inventory part, or, where the pack draws the inventory in pieces with no frame of its own
     * to cut, the pack's plain panel ({@link #panel}). A pack that hangs the inventory's three
     * rows under the container's box with no top border and boxes the hotbar apart leaves no
     * strip of its inventory part plain fill; such packs draw those pieces in their plain
     * panel's colour on every container's screen, whatever colour the container's own is.
     */
    public static Look inventory(Identifier texture) {
        Look own = look(texture, Part.INVENTORY);
        if (own.source() != Source.FALLBACK) return own;
        Look plain = panel();
        return plain.source() == Source.PACK ? plain : own;
    }

    /** Works out one look, in the order the class doc gives. Never throws: anything unreadable is MenuKit's sprite. */
    private static Look resolve(Identifier texture, Part part) {
        // No game (MenuKit's headless checks draw panels too): MenuKit's own sprite.
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return new Look(texture, part, Source.VANILLA, null, SPRITE_FILL, false);
        ResourceManager rm = mc.getResourceManager();
        boolean slotSprite = PackArt.fromPack(rm.getResource(ControlLook.file(SLOT_SPRITE)), PackArt.VANILLA_PACK);

        // 1. A pack restyled MenuKit's own sprite: that, whatever the container texture.
        Optional<Resource> sprite = rm.getResource(ControlLook.file(PANEL_SPRITE));
        if (PackArt.fromPack(sprite, PackArt.MENUKIT_PACK)) {
            Integer face = PackArt.faceColour(sprite.get());
            if (face == null) LOG.warn("Couldn't read the overridden {}; its text reads against MenuKit's grey", PANEL_SPRITE);
            return new Look(texture, part, Source.OVERRIDE, null, face == null ? SPRITE_FILL : face, slotSprite);
        }

        // 2. Vanilla's own texture (or none at all): MenuKit's sprite, unchanged.
        Optional<Resource> res = rm.getResource(texture);
        if (!PackArt.fromPack(res, PackArt.VANILLA_PACK)) return new Look(texture, part, Source.VANILLA, null, SPRITE_FILL, slotSprite);

        // 3. Cut from the pack's texture, or MenuKit's sprite when it can't be cut cleanly.
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            Cut cut = analyse(new Pixels(img), part == Part.INVENTORY);
            Source source = cut != null && cut.clean() ? Source.PACK : Source.FALLBACK;
            LOG.debug("{} {} from {}: {}", texture, part, res.get().sourcePackId(), source);
            return new Look(texture, part, source, cut, SPRITE_FILL, slotSprite);
        } catch (Exception e) {
            LOG.warn("Couldn't cut a panel look from {}: {}", texture, e.toString());
            return new Look(texture, part, Source.FALLBACK, null, SPRITE_FILL, slotSprite);
        }
    }

    // The cut, pure over pixels

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

    /**
     * A texture's pixels, read in its 256-unit coordinates, one sample per unit: the first
     * pixel of the unit, where its top-left corner falls (rounded to the nearest pixel edge
     * on art finer than the units). So a 512 px (32x) texture reads every other pixel, a
     * 384 px one two pixels in every three, and a 128 px one each pixel twice.
     */
    static final class Pixels {
        final int w, h;
        private final int[] argb;
        private final int nativeW, nativeH;
        /** Pixels per unit. */
        private final double scale;

        Pixels(NativeImage img) {
            this(img.getWidth(), img.getHeight(), copy(img));
        }

        /** Pixels from {@code argb}, {@code nativeW} wide and {@code nativeH} tall: for the headless checks. */
        Pixels(int nativeW, int nativeH, int[] argb) {
            this.nativeW = nativeW;
            this.nativeH = nativeH;
            this.argb = argb;
            this.scale = nativeW / 256.0;
            this.w = 256;
            this.h = (int) Math.round(nativeH / scale);
        }

        // ponytail: a copy of the whole texture, read once per texture and reload; a 4096 px (256x) pack's is 64 MB for the moment it takes. Sample the image in place if that ever matters.
        private static int[] copy(NativeImage img) {
            int[] out = new int[img.getWidth() * img.getHeight()];
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) out[x + y * img.getWidth()] = img.getPixel(x, y);
            return out;
        }

        int at(int x, int y) {
            if (x < 0 || y < 0 || x >= w || y >= h) return 0;
            double edge = scale > 1 ? 0.5 : 0;
            int px = Math.min(nativeW - 1, (int) (x * scale + edge)), py = Math.min(nativeH - 1, (int) (y * scale + edge));
            return argb[px + py * nativeW];
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
     * The cut of one part of a texture given as pixels ({@code width x height} ARGB, row by
     * row), as {@link #look} cuts a pack's: for the headless checks.
     */
    @VisibleForTesting
    public static @Nullable Cut cut(int width, int height, int[] argb, Part part) {
        return analyse(new Pixels(width, height, argb), part == Part.INVENTORY);
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
        // vanilla's split (a few rows below it, typically), in the outer border's colour.
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

        // Border widths: 7, or less where the art starts right of 0 (a narrower frame), so a
        // border never takes in the slots at x 7.
        int l = Math.max(2, Math.min(C, GRID_X0 - x0)), r = Math.max(2, Math.min(C, x1 - GRID_X1)), t = C, b = C;
        int contEnd = 17 + 18 * ((split - 17) / 18);
        final int bot = bottom;
        BiPredicate<Integer, Integer> slot = (x, y) -> x >= GRID_X0 && x < GRID_X1
                && ((y >= bot - 83 && y < bot - 29) || (y >= bot - 25 && y < bot - 7) || (y >= 17 && y < contEnd));

        // The fill: the colour of the longest run of flat rows in the part's slot-free
        // strips. Wood grain has no flat run (the look falls back); a zigzag along the title
        // is skipped for the plain rows under it.
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

        // Each side's usual profile across its border.
        List<int[]> lefts = new ArrayList<>(), rights = new ArrayList<>(), tops = new ArrayList<>(), bots = new ArrayList<>();
        int innerFrom = top + t, innerTo = inventory ? bottom - b : p1;
        for (int y = innerFrom; y < innerTo; y++) { lefts.add(rowOf(p, y, x0, l)); rights.add(rowOf(p, y, x1 - r, r)); }
        for (int x = x0 + l; x < x1 - r; x++) { tops.add(colOf(p, x, top, t)); if (!mirror) bots.add(colOf(p, x, bottom - b, b)); }
        if (lefts.isEmpty() || tops.isEmpty()) return null;
        int[] leftM = modal(lefts), rightM = modal(rights), topM = modal(tops), botM = mirror ? null : modal(bots);

        // Top corners grow while an ornament runs past them (brackets over the corners), as
        // squares, never into the slots: one that would is left at its border's size.
        int sTop = Math.max(t, Math.max(l, r));
        if (uniform) {
            sTop = Math.max(grow(p, false, x0, x1, top, l, r, t, leftM, rightM, topM, fill, slot),
                    grow(p, true, x0, x1, top, l, r, t, leftM, rightM, topM, fill, slot));
        }
        int sBot = mirror ? sTop : Math.max(b, Math.max(l, r));

        // Edge strips: the longest run along each side like its usual profile, clear of the
        // corners and of decoration (a latch, a hinge).
        Strip topRun = run(p, x0 + sTop, x1 - sTop, x -> colOf(p, x, top, t), topM);
        Strip botRun = mirror ? topRun : run(p, x0 + sBot, x1 - sBot, x -> colOf(p, x, bot - b, b), botM);
        int sideFrom = top + sTop, sideTo = mirror || !inventory ? p1 : bottom - sBot;
        final int fx0 = x0, fx1 = x1;
        Strip leftRun = run(p, sideFrom, sideTo, y -> rowOf(p, y, fx0, l), leftM);
        Strip rightRun = run(p, sideFrom, sideTo, y -> rowOf(p, y, fx1 - r, r), rightM);

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

        boolean clean = uniform && Math.min(Math.min(topRun.length(), botRun.length()), Math.min(leftRun.length(), rightRun.length())) >= 2;
        return new Cut(x0, x1, top, bottom, mirror, l, r, t, b, sTop, sBot, fill,
                new Edges(topRun, botRun, leftRun, rightRun), sideFrom, sideTo, slotV, slotOk, clean, insetL, insetR, p.w, p.h);
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

    /** The longest run in {@code [from, to)} whose profile is like {@code ref}. */
    private static Strip run(Pixels p, int from, int to, IntFunction<int[]> profile, int[] ref) {
        int bestStart = from, best = 0, start = -1;
        for (int i = from; i <= to; i++) {
            boolean ok = i < to && same(profile.apply(i), ref);
            if (ok && start < 0) start = i;
            if (!ok && start >= 0) {
                if (i - start > best) { best = i - start; bestStart = start; }
                start = -1;
            }
        }
        return new Strip(bestStart, best);
    }

    /**
     * How big a top corner must be to hold its ornament: the smallest square from its
     * border's size whose next two shells (the row below it and the column beside it) are
     * plain (the side's border profile, the top's, the fill). One that would reach a slot
     * stays at the border's size.
     */
    private static int grow(Pixels p, boolean right, int x0, int x1, int top, int l, int r, int t,
                            int[] leftM, int[] rightM, int[] topM, int fill,
                            BiPredicate<Integer, Integer> slot) {
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
                                           BiPredicate<Integer, Integer> slot, int s) {
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
        // Corners can't be bigger than half the panel.
        int sT = Math.min(c.sTop(), Math.min(w / 2, h / 2)), sB = Math.min(c.sBot(), Math.min(w / 2, h / 2));

        // The fill under everything, inside the borders.
        drawFill(g, x + c.l(), y + c.t(), w - c.l() - c.r(), h - c.t() - c.b(), look);

        // Edges, tiled from their clean strips: the top's along the top, the sides' down the
        // sides, the bottom's (or the top's mirrored) along the bottom.
        Edges e = c.edges();
        strip(g, c, tex, x + sT, y, w - 2 * sT, c.t(), e.top().from(), c.top(), e.top().length(), c.t(), false);
        strip(g, c, tex, x, y + sT, c.l(), h - sT - sB, c.x0(), e.left().from(), c.l(), e.left().length(), false);
        strip(g, c, tex, x + w - c.r(), y + sT, c.r(), h - sT - sB, c.x1() - c.r(), e.right().from(), c.r(), e.right().length(), false);
        if (c.mirror()) {
            strip(g, c, tex, x + sB, y + h - c.t(), w - 2 * sB, c.t(), e.top().from(), c.top(), e.top().length(), c.t(), true);
            corner(g, c, tex, x, y + h - sB, sB, c.x0(), c.top(), false, true);
            corner(g, c, tex, x + w - sB, y + h - sB, sB, c.x1() - sB, c.top(), true, true);
        } else {
            strip(g, c, tex, x + sB, y + h - c.b(), w - 2 * sB, c.b(), e.bottom().from(), c.bottom() - c.b(), e.bottom().length(), c.b(), false);
            piece(g, c, tex, x, y + h - sB, sB, sB, c.x0(), c.bottom() - sB, sB, sB, false);
            piece(g, c, tex, x + w - sB, y + h - sB, sB, sB, c.x1() - sB, c.bottom() - sB, sB, sB, false);
        }
        corner(g, c, tex, x, y, sT, c.x0(), c.top(), false, false);
        corner(g, c, tex, x + w - sT, y, sT, c.x1() - sT, c.top(), true, false);
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

    /** Fills a rectangle with {@code look}'s fill: one colour, since a look is only cut from a flat fill. */
    private static void drawFill(GuiGraphicsExtractor g, int x, int y, int w, int h, Look look) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, look.fill());
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
     * the last copy cut short.
     */
    private static void strip(GuiGraphicsExtractor g, Cut c, Identifier tex, int x, int y, int w, int h,
                              int u, int v, int uw, int vh, boolean flip) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
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
     * What a panel's surface is at a screen point: the look a slot frame drawn there takes,
     * and whose fill text there is read against ({@link PackText#on}). {@code null} where
     * there is no surface (a card drawn over one).
     */
    @FunctionalInterface
    public interface SurfaceLooks {
        @Nullable Look at(int x, int y);
    }

    // ponytail: the surface being drawn on, a render-thread scope set around each panel and card; SlotRendering.drawSlotBackground (public, called by slot libraries) has no context to carry it in.
    private static @Nullable SurfaceLooks surface;

    /**
     * While a panel (or a card on it) renders, the surface its slot frames and text are on:
     * the panel's own ({@code Panel.surfaceLooks}), the host's for a Panel-look raised
     * panel, or {@code null} for none. Returns what was set before, for {@link #endSurface}.
     * Render thread.
     */
    public static @Nullable SurfaceLooks beginSurface(@Nullable SurfaceLooks looks) {
        SurfaceLooks before = surface;
        surface = looks;
        return before;
    }

    /** Puts back what {@link #beginSurface} replaced. */
    public static void endSurface(@Nullable SurfaceLooks before) {
        surface = before;
    }

    /** The surface's look at {@code (x, y)} now, or {@code null} outside any panel surface. */
    public static @Nullable Look surfaceAt(int x, int y) {
        SurfaceLooks s = surface;
        return s == null ? null : s.at(x, y);
    }
}
