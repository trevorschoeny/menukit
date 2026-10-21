package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.inject.PanelHost;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One mod's part of a shared panel ({@link SharedPanels}, 7.4.0): its elements, its order
 * in the panel, the mode it shows in and its own {@code visibleWhen}, plus the panel's own
 * declaration (where it goes per mode, its style, its axis and gap). Every contribution
 * carries the whole declaration, so whichever mods are installed can build the panel.
 *
 * <pre>{@code
 * SharedPanels.join("trevlar:gear", Contribution.builder(10, ModeScope.BOTH)
 *         .add(elytraSlot)
 *         .visibleWhen(config::elytraSlotOn)
 *         .in(ModeScope.SURVIVAL, Placement.around(OFFHAND, PanelPosition.region(OutsideRegion.TOP_CENTER)))
 *         .in(ModeScope.CREATIVE, Placement.around(ARMOUR, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
 *         .gap(0)
 *         .build());
 * }</pre>
 */
public final class Contribution {

    final int order;
    final ModeScope scope;
    final List<PanelElement> elements;
    final @Nullable BooleanSupplier visibleWhen;
    // The panel's own declaration, the same in every contribution when the mods agree.
    final Map<ModeScope, Placement> placements;
    final PanelStyle style;
    final boolean vertical;
    final int gap;

    private Contribution(Builder b) {
        this.order = b.order;
        this.scope = b.scope;
        this.elements = List.copyOf(b.elements);
        this.visibleWhen = b.visibleWhen;
        this.placements = Collections.unmodifiableMap(new EnumMap<>(b.placements));
        this.style = b.style;
        this.vertical = b.vertical;
        this.gap = b.gap;
    }

    /**
     * Starts a contribution. {@code order} places it in the panel's flow, lower first, ties
     * broken by mod id; the lowest also decides the panel's declaration when mods disagree.
     * {@code scope} is the mode its elements show in.
     */
    public static Builder builder(int order, ModeScope scope) {
        return new Builder(order, Objects.requireNonNull(scope, "scope"));
    }

    /** Whether its elements show now: its scope covers the mode, and its own condition holds. */
    boolean shown() {
        return PanelHost.inScope(scope) && (visibleWhen == null || visibleWhen.getAsBoolean());
    }

    /** What of the panel's declaration differs from {@code other}'s, for the warning; empty when they agree. */
    List<String> differences(Contribution other) {
        List<String> out = new ArrayList<>();
        if (!placements.equals(other.placements)) out.add("placements");
        if (style != other.style) out.add("style");
        if (vertical != other.vertical) out.add("axis");
        if (gap != other.gap) out.add("gap");
        return out;
    }

    /** Builds a {@link Contribution}. Needs at least one element and one placement. */
    public static final class Builder {
        private final int order;
        private final ModeScope scope;
        private final List<PanelElement> elements = new ArrayList<>();
        private @Nullable BooleanSupplier visibleWhen;
        private final EnumMap<ModeScope, Placement> placements = new EnumMap<>(ModeScope.class);
        private PanelStyle style = PanelStyle.RAISED;
        private boolean vertical = false;
        private int gap = Flow.DEFAULT_GAP;

        private Builder(int order, ModeScope scope) {
            this.order = order;
            this.scope = scope;
        }

        /** Appends elements, laid out in the panel's flow in this order. */
        public Builder add(PanelElement... elements) {
            for (PanelElement e : elements) this.elements.add(Objects.requireNonNull(e, "element"));
            return this;
        }

        /** Shows the elements only while {@code condition} holds, read every frame; hidden, they take no room. */
        public Builder visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * The panel's placement in {@code scope}, as {@link Panel.Builder#in}: once per scope.
         *
         * @throws IllegalStateException when {@code scope} already has a placement
         */
        public Builder in(ModeScope scope, Placement placement) {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(placement, "placement");
            if (placements.putIfAbsent(scope, placement) != null) {
                throw new IllegalStateException("Contribution already has a placement for " + scope + "; declare each scope once");
            }
            return this;
        }

        /** The panel's style. Default {@link PanelStyle#RAISED}. */
        public Builder style(PanelStyle style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        /** Lays the panel out top to bottom ({@link Flow.Builder#vertical()}) instead of in a row. */
        public Builder vertical() {
            this.vertical = true;
            return this;
        }

        /** Pixels between neighbouring elements, across contributions too. Default {@link Flow#DEFAULT_GAP}; 0 keeps slots on vanilla's 18 px pitch. */
        public Builder gap(int px) {
            if (px < 0) throw new IllegalArgumentException("gap must be >= 0, was " + px);
            this.gap = px;
            return this;
        }

        /**
         * @throws IllegalStateException with no element, or with no placement (every
         *         contribution carries the panel's placement)
         */
        public Contribution build() {
            if (elements.isEmpty()) throw new IllegalStateException("Contribution has no elements; add(...) at least one");
            if (placements.isEmpty()) {
                throw new IllegalStateException("Contribution has no placement: every contribution carries the panel's "
                        + "in(ModeScope, Placement), so the panel builds with only one mod installed");
            }
            return new Contribution(this);
        }
    }
}
