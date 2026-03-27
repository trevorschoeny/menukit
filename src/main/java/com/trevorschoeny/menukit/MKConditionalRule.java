package com.trevorschoeny.menukit;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A rule that conditionally inserts elements into the panel tree based on
 * matching predicates. Rules are evaluated after menu resolution -- they
 * walk each panel's {@link MKGroupDef} tree and insert new children
 * before or after elements that match the condition.
 *
 * <p>Rules can match ANY element type via {@code Predicate<MKGroupChild>},
 * not just SlotGroups. This makes the system general-purpose.
 *
 * <p>Example:
 * <pre>{@code
 * MenuKit.conditionalElement("sort_button")
 *     .when(child -> child instanceof MKGroupChild.SlotGroup sg
 *             && sg.containerType() == MKContainerType.SIMPLE)
 *     .insertAfter()
 *     .elements(ctx -> new MKGroupChild.Button(sortButtonDef, "sort:" + ctx.matchedElementId()))
 *     .register();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> conditional element system.
 */
public record MKConditionalRule(
        /** Unique identifier for this rule. Used for duplicate-insertion tracking. */
        String id,
        /** Matches ANY element type in the panel tree. */
        Predicate<MKGroupChild> condition,
        /** Whether to insert BEFORE or AFTER the matched element. */
        Placement placement,
        /** Factories that create the new elements to insert. */
        List<ElementFactory> elements,
        /** If non-null and returns true, this rule is skipped entirely. */
        @Nullable BooleanSupplier disabledWhen
) {

    /** Where to insert the new elements relative to the matched element. */
    public enum Placement {
        /** Insert before the matched element in the parent's children list. */
        BEFORE,
        /** Insert after the matched element in the parent's children list. */
        AFTER
    }

    /**
     * Creates a single element to insert into the panel tree.
     * Receives the {@link MKConditionalContext} describing the matched element.
     */
    @FunctionalInterface
    public interface ElementFactory {
        MKGroupChild create(MKConditionalContext ctx);
    }

    // ── Fluent Builder ──────────────────────────────────────────────────────

    /**
     * Starts building a conditional rule with the given ID.
     *
     * @param ruleId unique identifier for this rule
     * @return a new Builder
     */
    public static Builder builder(String ruleId) {
        return new Builder(ruleId);
    }

    /**
     * Fluent builder for {@link MKConditionalRule}.
     * Usage: {@code MKConditionalRule.builder("my_rule").when(...).insertAfter().elements(...).register()}
     */
    public static class Builder {
        private final String id;
        private @Nullable Predicate<MKGroupChild> condition;
        private Placement placement = Placement.AFTER;
        private final List<ElementFactory> factories = new ArrayList<>();
        private @Nullable BooleanSupplier disabledWhen;

        Builder(String id) {
            this.id = id;
        }

        /** Sets the matching predicate. Matches any element type in the tree. */
        public Builder when(Predicate<MKGroupChild> predicate) {
            this.condition = predicate;
            return this;
        }

        /** New elements will be inserted BEFORE the matched element. */
        public Builder insertBefore() {
            this.placement = Placement.BEFORE;
            return this;
        }

        /** New elements will be inserted AFTER the matched element. */
        public Builder insertAfter() {
            this.placement = Placement.AFTER;
            return this;
        }

        /** Adds element factories that create the new children. */
        public Builder elements(ElementFactory... factories) {
            for (ElementFactory f : factories) this.factories.add(f);
            return this;
        }

        /** Adds a single element factory. */
        public Builder element(ElementFactory factory) {
            this.factories.add(factory);
            return this;
        }

        /** Disables this rule when the predicate returns true. */
        public Builder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate;
            return this;
        }

        /** Builds the rule and registers it with MenuKit. */
        public MKConditionalRule register() {
            if (condition == null) {
                throw new IllegalStateException(
                        "[MenuKit] Conditional rule '" + id + "' has no .when() predicate");
            }
            if (factories.isEmpty()) {
                throw new IllegalStateException(
                        "[MenuKit] Conditional rule '" + id + "' has no .elements()");
            }
            MKConditionalRule rule = new MKConditionalRule(
                    id, condition, placement, List.copyOf(factories), disabledWhen);
            MenuKit.registerConditionalRule(rule);
            return rule;
        }

        /** Builds the rule WITHOUT registering it. For testing or deferred registration. */
        public MKConditionalRule build() {
            if (condition == null) {
                throw new IllegalStateException(
                        "[MenuKit] Conditional rule '" + id + "' has no .when() predicate");
            }
            return new MKConditionalRule(
                    id, condition, placement, List.copyOf(factories), disabledWhen);
        }
    }
}
