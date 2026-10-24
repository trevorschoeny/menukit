package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.ContainerRegion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The container region's section titles (7.4.0): what each shown section is called this
 * frame, and how far the first one's title moves clear of the list's top corner.
 */
@ApiStatus.Internal
public final class RegionTitles {

    private RegionTitles() {}

    /**
     * The titles the shown, titled sections draw, in stacking order, pure. Each its own
     * title; but with two or more, one that comes out blank (a pack blanked vanilla's key)
     * takes a name, so the player can tell the containers apart: its own {@code names}
     * entry where that isn't blank (its {@code name(...)}, else its container's name,
     * {@link #sourceName}), else vanilla's own English for the title's translation key. Names that come out the same are numbered in order
     * ("Lime Shulker Box 1", "Lime Shulker Box 2"). A blank title with nothing to stand in
     * for it stays blank.
     */
    public static List<Component> shownTitles(List<Component> titles, List<@Nullable Component> names) {
        List<Component> out = new ArrayList<>(titles);
        if (titles.size() < 2) return out;
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            if (!blank(titles.get(i))) continue;
            Component name = names.get(i);
            if (blank(name)) name = vanillaWords(titles.get(i));
            if (blank(name)) continue;
            out.set(i, name);
            count.merge(name.getString(), 1, Integer::sum);
        }
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == titles.get(i)) continue;   // its own title, not a name
            String key = out.get(i).getString();
            if (count.get(key) < 2) continue;
            out.set(i, Component.empty().append(out.get(i)).append(" " + seen.merge(key, 1, Integer::sum)));
        }
        return out;
    }

    static boolean blank(@Nullable Component c) {
        return c == null || c.getString().isBlank();
    }

    /**
     * What vanilla's own English says for {@code title}'s translation key, whatever a pack
     * made of it, or {@code null} when it has none ("container.shulkerBox": "Shulker Box").
     */
    // Vanilla's built-in English, not the player's language: only a section with no name and no block to name it by gets here.
    private static @Nullable Component vanillaWords(@Nullable Component title) {
        if (title == null || !(title.getContents() instanceof TranslatableContents t)) return null;
        String words = Language.DEFAULT_INSTANCE.getOrDefault(t.getKey(), "");
        return words.isBlank() ? null : Component.literal(words);
    }

    /**
     * What a section is called by its container ({@code source}: its own, or for the screen's
     * own container the block it was opened on): the name the player gave it, else its block's
     * name in the player's language ("Lime Shulker Box"), which packs don't blank. {@code null}
     * with neither.
     */
    @VisibleForTesting
    public static @Nullable Component sourceName(ContainerRegion.@Nullable Source source) {
        if (source == null) return null;
        if (source.customName() != null) return Component.literal(source.customName());
        return source.block() == null ? null : BuiltInRegistries.BLOCK.getValue(source.block()).getName();
    }

    /**
     * How far right the first shown section's title goes, so it stays clear of the list's top
     * corner where the pack decorates it (brackets drawn over the corner can run past
     * vanilla's title at x 8): to a pixel past the corner ({@code corner}, the list's look's).
     * None for a plain corner.
     */
    static int indent(int corner) {
        return Math.max(0, corner + 1 - (RegionLayout.INSET_X + 1));
    }

    /**
     * The container the player opened {@code screen} on, for the sections that are the
     * screen's own container (those with no source): the block under the crosshair as the
     * screen opened, the one the right click used, if it holds a container the size of the
     * screen's own (the menu's first slot's) and the screen has its title. So a lime box's own
     * screen knows it is a lime box, for its name, its dye and the pack's rules. {@code null}
     * for a screen opened from an item, or by a key while looking at a container of another
     * size or title (a backpack's screen in front of a chest), and on a double chest.
     *
     * <p>The title is the container's default name: the client's block entity never knows the
     * name a player gave it, so a named container is not found, and its section takes what
     * OptiGUI picked for the screen, which is the same art; its title is its name, so it never
     * needs another.
     */
    // Read from the crosshair at open, not from a use hook, so a screen opened by a key while looking at another unnamed container of the same size and kind takes that one.
    static ContainerRegion.@Nullable Source openedContainer(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(screen instanceof AbstractContainerScreen<?> acs) || acs.getMenu() == null || acs.getMenu().slots.isEmpty()) return null;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        Container own = acs.getMenu().getSlot(0).container;
        if (!(be instanceof Container c) || own instanceof Inventory || c.getContainerSize() != own.getContainerSize()) return null;
        if (be instanceof Nameable n && !n.getDisplayName().getContents().equals(screen.getTitle().getContents())) return null;
        return ContainerRegion.Source.of(be);
    }
}
