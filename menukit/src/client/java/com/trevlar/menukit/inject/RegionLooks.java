package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.core.OptiGuiRules;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.inject.RegionDeclarations.Declared;
import com.trevlar.menukit.inject.ContainerRegions.Frame;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The container region's panels following resource packs (7.4.0): which container texture
 * each section and the inventory panel take, and the two panels' backgrounds drawn in those
 * looks. The looks themselves are worked out once a frame ({@link ContainerRegions.Frame});
 * everything here that draws reads them from there.
 */
@ApiStatus.Internal
public final class RegionLooks {

    private RegionLooks() {}

    /**
     * The container texture {@code screen} draws its own image from, whose inventory half
     * the inventory panel takes, so it matches the inventory vanilla would draw on that
     * screen under the pack: the texture its background pass blits ({@link ScreenBackground#image}:
     * a hopper's screen is the hopper's), and with OptiGUI the art OptiGUI gives the screen's
     * own container for it.
     */
    // The chest's until a screen of its kind has drawn its image once, so the first frame of the first such screen opened may show the chest's look.
    static Identifier screenTexture(@Nullable Screen screen) {
        Identifier own = screen == null ? null : ScreenBackground.image(screen);
        return OptiGuiRules.onScreen(own == null ? PackLook.CHEST : own);
    }

    /**
     * The texture section {@code s} is drawn from. Its own container's texture first: with a
     * source, the one its block's screen draws ({@link #blockTexture}), so a source and
     * {@code texture(...)} never disagree; else its {@code texture(...)} (a section with no
     * block behind it, or the screen's own container). With OptiGUI, the one OptiGUI's rules
     * give that container: its source, or without one the container the screen was opened
     * on ({@code opened}), else the one OptiGUI gives the open screen's.
     */
    static Identifier sectionTexture(Declared s, ContainerRegion.@Nullable Source opened) {
        ContainerRegion.Source c = s.source() == null ? opened : s.source().get();
        Identifier own = s.source() == null ? null : blockTexture(c);
        if (own == null) own = s.texture();
        if (!OptiGuiRules.LOADED) return own;
        if (c == null) return OptiGuiRules.onScreen(own);
        String title = s.title() == null ? "" : s.title().get().getString();
        return OptiGuiRules.forContainer(own, c.block(), c.customName(), title, c.pos());
    }

    /**
     * The container texture {@code source}'s block's own screen draws, or {@code null} for no
     * block or one whose texture the region doesn't know: a shulker box's for every shulker
     * box, the chest's for chests, ender chests and barrels. Vanilla keeps each screen's
     * texture inside its screen class, with no table from a block to it, so these are named.
     */
    // Only the two textures laid out like the chest's, the only ones a section's look can be cut from; any other block's section takes its texture(...).
    @VisibleForTesting
    public static @Nullable Identifier blockTexture(ContainerRegion.@Nullable Source source) {
        if (source == null || source.block() == null) return null;
        Block block = BuiltInRegistries.BLOCK.getValue(source.block());
        if (block instanceof ShulkerBoxBlock) return PackLook.SHULKER_BOX;
        if (block instanceof AbstractChestBlock<?> || block instanceof BarrelBlock) return PackLook.CHEST;
        return null;
    }

    /** Every shown section's look this frame, in stacking order, {@code null} while it is hidden. */
    static PackLook.@Nullable Look[] sectionLooks(List<Declared> sections, boolean[] shown, ContainerRegion.@Nullable Source opened) {
        PackLook.Look[] out = new PackLook.Look[sections.size()];
        for (int i = 0; i < out.length; i++) {
            if (shown[i]) out[i] = PackLook.look(sectionTexture(sections.get(i), opened), PackLook.Part.CONTAINER);
        }
        return out;
    }

    /**
     * The list's own look, where no section's is: the first shown section's (whose slice
     * holds the list's top), else the screen's own container texture's.
     */
    static PackLook.Look listLook(PackLook.@Nullable Look[] looks, @Nullable Screen screen) {
        for (PackLook.Look l : looks) if (l != null) return l;
        return PackLook.look(screenTexture(screen), PackLook.Part.CONTAINER);
    }

    /**
     * Whether a section in {@code own} shows as a block of its own on a list in {@code list}:
     * its fill unlike the first shown section's, so its title takes vanilla's room above it
     * ({@link RegionLayout#TITLE_INSET}).
     */
    static boolean ownsBlock(PackLook.@Nullable Look own, PackLook.Look list) {
        return own != null && own != list && own.fill() != list.fill();
    }

    /**
     * What the surface is at a point in the container list this frame: the look of the
     * section whose slice it is in ({@link RegionLayout#bands}), else the list's. So a slot
     * frame takes its section's look, and text a color that reads on its section's fill.
     */
    static PackLook.@Nullable Look listSurfaceAt(int x, int y) {
        ContainerRegions.State state = ContainerRegions.state;
        Frame f = state.frame;
        Geometry g = state.geometry;
        if (f == null) return null;
        if (g == null || state.tops.length != f.looks().length) return f.list();
        int i = RegionLayout.bandAt(RegionLayout.bands(g, state.tops, state.scroll), x, y);
        return i < 0 || f.looks()[i] == null ? f.list() : f.looks()[i];
    }

    /** What the surface is anywhere in the inventory panel this frame: the inventory half's look. */
    static PackLook.@Nullable Look inventorySurfaceAt(int x, int y) {
        Frame f = ContainerRegions.state.frame;
        return f == null ? null : f.inventory();
    }

    /**
     * The container list, each section a slice of its own container's screen, so a box in the
     * list looks as its own screen does under the pack, and where two meet the colors just
     * change, with no seam drawn between them: in each section's slice of the list ({@link
     * RegionLayout#bands} out to the list's edges), the list drawn whole in that section's
     * look, cut to the slice. So the list's left and right edges at a section's height are
     * that section's texture's, its fill too; the top is the first section's, the bottom the
     * last's, and the outline runs on unbroken. Slices next to each other in one look are one,
     * so with no pack (every look MenuKit's own) the list is drawn once, exactly as before. The
     * scrollbar lane is inside it while it scrolls; everything the sections draw goes over it,
     * their text in a color that reads on their own fill.
     *
     * <p>Where the list and the inventory panel are one frame (the fitted frame), each slice is of a
     * panel the whole frame's height, so the shown section's art runs straight on into the
     * inventory part ({@link InventoryFrame}, the frame's other slice), with no edge between.
     */
    static final class Backdrop implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = ContainerRegions.state.geometry; return g == null ? 0 : g.width(); }
        @Override public int getHeight() { Geometry g = ContainerRegions.state.geometry; return g == null ? 0 : g.height(); }

        @Override
        public void render(RenderContext ctx) {
            ContainerRegions.State state = ContainerRegions.state;
            Geometry g = state.geometry;
            Frame f = state.frame;
            if (g == null || f == null) return;
            GuiGraphicsExtractor graphics = ctx.graphics();
            int ox = ctx.originX(), oy = ctx.originY(), w = g.width(), h = g.height();
            // The panel each slice is cut from: the list's, or the whole frame's where it is one.
            int panelHeight = g.shape().joined() ? g.frameHeight() : h;
            PackLook.Look[] looks = f.looks();
            // The panel's origin is the list's top-left; the slices are from the screen's.
            int dy = oy - g.top();
            if (state.tops.length != looks.length) {
                slice(graphics, ox, oy, w, panelHeight, oy, oy + h, f.list());
                return;
            }
            int[][] slices = RegionLayout.bands(g, state.tops, state.scroll);
            List<Integer> shown = new ArrayList<>();
            for (int i = 0; i < slices.length; i++) if (slices[i] != null && looks[i] != null) shown.add(i);
            if (shown.isEmpty()) {
                slice(graphics, ox, oy, w, panelHeight, oy, oy + h, f.list());
                return;
            }
            for (int k = 0; k < shown.size(); ) {
                PackLook.Look look = looks[shown.get(k)];
                int y0 = slices[shown.get(k)][1], m = k + 1;
                while (m < shown.size() && looks[shown.get(m)] == look) m++;
                int y1 = slices[shown.get(m - 1)][3];
                slice(graphics, ox, oy, w, panelHeight, y0 + dy, y1 + dy, look);
                k = m;
            }
        }
    }

    /**
     * A {@code w x h} panel at {@code (x, y)} in {@code look}, drawn only from {@code y0} to
     * {@code y1}: whole, with no scissor, where that is all of it.
     */
    private static void slice(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int y0, int y1, PackLook.Look look) {
        if (y0 <= y && y1 >= y + h) {
            PackLook.drawPanel(graphics, x, y, w, h, look);
            return;
        }
        graphics.enableScissor(x, y0, x + w, y1);
        PackLook.drawPanel(graphics, x, y, w, h, look);
        graphics.disableScissor();
    }

    /**
     * The inventory panel's look: a panel surface in the look of the inventory half of the
     * screen's container texture under the active resource packs (MenuKit's raised panel
     * with vanilla's own), and a slot frame under each of the player's inventory slots,
     * where vanilla's image had them, in the same look. The slots themselves are vanilla's,
     * drawn over it in vanilla's pass. Where the list and the inventory panel are one frame
     * (the fitted frame), the surface is the whole frame's panel cut to this part, the list's slice
     * above it ({@link Backdrop}).
     */
    static final class InventoryFrame implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = ContainerRegions.state.geometry; return g == null ? 0 : g.inventory().imageWidth(); }
        @Override public int getHeight() { Geometry g = ContainerRegions.state.geometry; return g == null ? 0 : g.inventory().imageHeight(); }

        @Override
        public void render(RenderContext ctx) {
            ContainerRegions.State state = ContainerRegions.state;
            Geometry g = state.geometry;
            Frame f = state.frame;
            if (g == null || f == null || !(state.screen.get() instanceof AbstractContainerScreen<?> acs)) return;
            Reference inv = g.inventory();
            int above = g.shape().joined() ? inv.topPos() - g.top() : 0;
            slice(ctx.graphics(), ctx.originX(), ctx.originY() - above, inv.imageWidth(), inv.imageHeight() + above,
                    ctx.originY(), ctx.originY() + inv.imageHeight(), f.inventory());
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
            for (Slot slot : acs.getMenu().slots) {
                if (RegionBounds.stays(slot) && slot.isActive()) {
                    PackLook.drawSlot(ctx.graphics(), acc.mk$getLeftPos() + slot.x - 1, acc.mk$getTopPos() + slot.y - 1, f.inventory());
                }
            }
        }
    }
}
