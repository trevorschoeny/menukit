package com.trevorschoeny.menukit.region;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.container.MKContainerDef;

import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Static registry that defines, for each vanilla menu type, what container
 * groups exist and which menu slot indices they map to.
 *
 * <p>This is the single source of truth for "what slots belong to what container"
 * across all vanilla screens. When a menu is constructed, MenuKit reads this
 * mapping to auto-create {@link MKContainer} wrappers.
 *
 * <p>Layout data is defined in {@link MKContextLayout} (shared with
 * {@link MKRegionRegistry}) and converted to {@link SlotGroup}s here.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKContainerMapping {

    /**
     * A group of contiguous slots in a menu that belong to one logical container.
     *
     * @param name          MK container name (e.g., "mk:hotbar", "mk:chest")
     * @param menuSlotStart first menu slot index (inclusive)
     * @param menuSlotEnd   last menu slot index (inclusive)
     * @param persistence   how items in this group behave
     */
    public record SlotGroup(
            String name,
            int menuSlotStart,
            int menuSlotEnd,
            MKContainerDef.Persistence persistence
    ) {
        /** Number of slots in this group. */
        public int size() {
            return menuSlotEnd - menuSlotStart + 1;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns all slot groups for a given menu instance and context.
     * The player inventory groups (hotbar, main, armor, offhand) are included
     * automatically for menus that contain them.
     *
     * @param menu    the menu instance (used to determine slot count for variable-size menus)
     * @param context the MKContext (determines which container-specific groups exist)
     * @return list of SlotGroups in this menu
     */
    public static List<SlotGroup> getSlotGroups(AbstractContainerMenu menu, @Nullable MKContext context) {
        List<SlotGroup> groups = new ArrayList<>();

        if (context == null) return groups;

        // Resolve all layouts (context + player inventory) from the shared source of truth,
        // then convert each resolved SlotLayout into a SlotGroup.
        for (MKContextLayout.SlotLayout layout : MKContextLayout.resolveForMenu(context, menu)) {
            groups.add(new SlotGroup(
                    layout.name(),
                    layout.menuSlotStart(),
                    layout.menuSlotEnd(),
                    layout.persistence()
            ));
        }

        return groups;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Counts vanilla slots in a menu (excludes appended MKSlots).
     * Delegates to {@link MKContextLayout#countNonMKSlots(AbstractContainerMenu)}.
     */
    static int countVanillaSlots(AbstractContainerMenu menu) {
        return MKContextLayout.countNonMKSlots(menu);
    }

    /**
     * Returns all container names that exist in a given context (static lookup).
     * Useful for mod authors to know what containers are available without a menu instance.
     *
     * <p>Delegates to {@link MKContextLayout#getNames(MKContext)}.
     */
    public static List<String> getContainerNames(MKContext context) {
        return MKContextLayout.getNames(context);
    }
}
