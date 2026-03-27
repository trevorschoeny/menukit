package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects keybind conflicts between an {@link MKKeybind} and all registered
 * {@link KeyMapping} instances. Unlike vanilla's conflict check (which only
 * compares the base key via {@code KeyMapping.same()}), this compares full
 * key sets -- two bindings with different key combos are NOT considered
 * conflicting even if they share a key.
 *
 * <p><b>Conflict rules:</b>
 * <ul>
 *   <li>MKKeyMapping vs MKKeybind: exact key set match = conflict</li>
 *   <li>Vanilla KeyMapping vs MKKeybind: vanilla's single key is wrapped as
 *       an MKKeybind for comparison. Only conflicts if the MKKeybind is also
 *       a single key and matches.</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public final class MKKeybindConflicts {

    private MKKeybindConflicts() {
        // Utility class -- no instances
    }

    /**
     * A single conflict entry.
     *
     * @param mapping  the conflicting KeyMapping
     * @param label    human-readable name of the binding
     * @param category human-readable category
     */
    public record Conflict(KeyMapping mapping, String label, String category) {
    }

    /**
     * Finds all registered KeyMappings that conflict with the given MKKeybind.
     *
     * <p>A conflict means the other mapping's effective key combo is an exact
     * match of the given keybind. Subset/superset relationships are NOT
     * conflicts (priority dispatch handles those at runtime).
     *
     * @param keybind  the keybind to check for conflicts
     * @param exclude  an optional KeyMapping to exclude (self). May be null.
     * @return list of conflicts, empty if none found
     */
    public static List<Conflict> findConflicts(MKKeybind keybind, KeyMapping exclude) {
        List<Conflict> conflicts = new ArrayList<>();

        if (keybind == null || keybind.isUnbound()) {
            return conflicts;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) {
            return conflicts;
        }

        for (KeyMapping km : mc.options.keyMappings) {
            // Skip self
            if (km == exclude) continue;

            // Skip unbound mappings
            if (km.isUnbound()) continue;

            // Get the other mapping's effective keybind for comparison
            MKKeybind otherBind;
            if (km instanceof MKKeyMapping mkKm) {
                otherBind = mkKm.getCombo();
            } else {
                // Vanilla KeyMapping: wrap its single key as an MKKeybind.
                // Vanilla mappings have no modifier awareness, so we treat
                // them as single-key combos.
                int keyCode = MKKeyMapping.getKeyCode(km);
                if (keyCode == InputConstants.UNKNOWN.getValue()) continue;
                otherBind = MKKeybind.ofKey(keyCode);
            }

            if (otherBind.isUnbound()) continue;

            // Exact key set match = conflict
            if (!keybind.equals(otherBind)) continue;

            // It's a real conflict
            String label = Component.translatable(km.getName()).getString();
            String category = km.getCategory().label().getString();
            conflicts.add(new Conflict(km, label, category));
        }

        return conflicts;
    }

    /**
     * Builds a tooltip listing all conflicts, formatted like vanilla:
     * <pre>
     * Conflicts with:
     *   - Attack/Destroy (Gameplay)
     *   - Sprint (Movement)
     * </pre>
     */
    public static List<Component> buildTooltipLines(List<Conflict> conflicts) {
        if (conflicts.isEmpty()) return List.of();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("key.menukit.conflicts_header")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));

        for (Conflict c : conflicts) {
            lines.add(Component.literal("  - " + c.label() + " (" + c.category() + ")")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        return lines;
    }
}
