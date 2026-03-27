package com.trevorschoeny.menukit;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A button attachment — a set of buttons that automatically attach to
 * containers matching a given type. Handles two modes transparently:
 *
 * <ul>
 *   <li><b>In-tree</b> — for MenuKit-built panels, buttons are injected
 *       into the panel's group tree at build time as a row before/after
 *       the matching SlotGroup.</li>
 *   <li><b>Overlay</b> — for vanilla panels (empty wrappers), a small
 *       overlay panel is created at menu resolution time, positioned
 *       relative to the matching region.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * MenuKit.buttonAttachment("sort_buttons")
 *     .forContainerType(MKContainerType.SIMPLE)
 *     .above()
 *     .gap(2)
 *     .disabledWhen(() -> !config.enableSorting)
 *     .buttons(regionName -> List.of(
 *         sortButton(regionName),
 *         moveButton(regionName)
 *     ))
 *     .register();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKButtonAttachment(
        /** Unique ID for this attachment. */
        String id,
        /** Which container type to attach to. */
        MKContainerType containerType,
        /** True = insert before SlotGroup in tree; false = after. */
        boolean above,
        /** Placement for overlay panels (vanilla containers). */
        MKRegionPlacement overlayPlacement,
        /** Fine-tuning offset for overlay panels (pixels). Added after placement. */
        int overlayOffsetX,
        /** Fine-tuning offset for overlay panels (pixels). Added after placement. */
        int overlayOffsetY,
        /** Gap between buttons in the row (pixels). */
        int gap,
        /** If non-null and returns true, the entire attachment is hidden. */
        @Nullable BooleanSupplier disabledWhen,
        /**
         * Factory that creates the button children for a given region name.
         * Called once per matching SlotGroup/region. The region name is passed
         * so buttons can bind click handlers to the correct container.
         */
        Function<String, List<MKGroupChild>> buttonFactory,
        /**
         * Optional predicate that excludes specific regions by name.
         * If non-null and returns true for a region name, the attachment
         * is NOT applied to that region. Null means no exclusions.
         */
        @Nullable Predicate<String> excludeRegion
) {

    /** Returns true if the given region name should be excluded from this attachment. */
    public boolean isExcluded(String regionName) {
        return excludeRegion != null && excludeRegion.test(regionName);
    }

    // ── Fluent Builder ───────────────────────────────────────────────────────

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private @Nullable MKContainerType containerType;
        private boolean above = true;
        private MKRegionPlacement overlayPlacement = MKRegionPlacement.TOP_RIGHT;
        private int overlayOffsetX = 0;
        private int overlayOffsetY = 0;
        private int gap = 2;
        private @Nullable BooleanSupplier disabledWhen;
        private @Nullable Function<String, List<MKGroupChild>> buttonFactory;
        private @Nullable Predicate<String> excludeRegion;

        Builder(String id) {
            this.id = id;
        }

        /** Match SlotGroups/regions with this container type. */
        public Builder forContainerType(MKContainerType type) {
            this.containerType = type;
            return this;
        }

        /** Place buttons above the SlotGroup/region (default). */
        public Builder above() {
            this.above = true;
            if (overlayPlacement == MKRegionPlacement.BOTTOM_LEFT
                    || overlayPlacement == MKRegionPlacement.BOTTOM_RIGHT) {
                overlayPlacement = MKRegionPlacement.TOP_RIGHT;
            }
            return this;
        }

        /** Place buttons below the SlotGroup/region. */
        public Builder below() {
            this.above = false;
            if (overlayPlacement == MKRegionPlacement.TOP_LEFT
                    || overlayPlacement == MKRegionPlacement.TOP_RIGHT) {
                overlayPlacement = MKRegionPlacement.BOTTOM_RIGHT;
            }
            return this;
        }

        /**
         * Sets the exact placement for overlay panels (vanilla containers).
         * Overrides the default set by {@link #above()}/{@link #below()}.
         * See {@link MKRegionPlacement} for the 8 placement options.
         */
        public Builder overlayPlacement(MKRegionPlacement placement) {
            this.overlayPlacement = placement;
            return this;
        }

        /** Fine-tuning pixel offset for overlay panels (added after placement). */
        public Builder overlayOffset(int x, int y) {
            this.overlayOffsetX = x;
            this.overlayOffsetY = y;
            return this;
        }

        /** Gap between buttons in the row (default 2px). */
        public Builder gap(int gap) {
            this.gap = gap;
            return this;
        }

        /** Hides the entire button row when the predicate returns true. */
        public Builder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate;
            return this;
        }

        /**
         * Factory that creates button children for a given region name.
         * The list elements should be {@link MKGroupChild.Button} instances.
         * They'll be wrapped in a row group automatically.
         */
        public Builder buttons(Function<String, List<MKGroupChild>> factory) {
            this.buttonFactory = factory;
            return this;
        }

        /**
         * Excludes regions whose name matches the predicate. The attachment
         * will NOT be applied to excluded regions, even if their container
         * type matches. Use for carving out exceptions from a broad type match.
         */
        public Builder excludeRegion(Predicate<String> predicate) {
            this.excludeRegion = predicate;
            return this;
        }

        /** Builds and registers the attachment with MenuKit. */
        public MKButtonAttachment register() {
            if (containerType == null) {
                throw new IllegalStateException(
                        "[MenuKit] ButtonAttachment '" + id + "' has no .forContainerType()");
            }
            if (buttonFactory == null) {
                throw new IllegalStateException(
                        "[MenuKit] ButtonAttachment '" + id + "' has no .buttons() factory");
            }
            MKButtonAttachment attachment = new MKButtonAttachment(
                    id, containerType, above, overlayPlacement,
                    overlayOffsetX, overlayOffsetY,
                    gap, disabledWhen, buttonFactory, excludeRegion);
            MenuKit.registerButtonAttachment(attachment);
            return attachment;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Creates the button row group for the given region name.
     * Calls the factory, wraps the result in a ROW MKGroupDef with the
     * attachment's gap and disabledWhen.
     *
     * @param regionName the container/region name for context-aware callbacks
     * @return a row group containing the buttons, or null if factory returns empty
     */
    public @Nullable MKGroupDef createButtonRow(String regionName) {
        List<MKGroupChild> children = buttonFactory.apply(regionName);
        if (children == null || children.isEmpty()) return null;

        return new MKGroupDef(
                MKGroupDef.LayoutMode.ROW,
                gap,
                0, 0, false,
                new ArrayList<>(children),
                disabledWhen,
                null, null);
    }

    /**
     * Returns the panel name used for overlay panels created by this attachment.
     * Format: {@code att:<id>:<regionName>}
     */
    public String overlayPanelName(String regionName) {
        return "att:" + id + ":" + regionName;
    }
}
