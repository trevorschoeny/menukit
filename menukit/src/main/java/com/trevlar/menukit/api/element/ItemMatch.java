package com.trevlar.menukit.api.element;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.EnchantmentPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An item rule: a stack and how another stack has to match it. What an
 * {@code ItemPicker} slot holds, and the one comparison every mod uses, so none
 * writes its own.
 *
 * <pre>{@code
 * // A rule the player set with an item picker, tested against a stack in play.
 * if (rule.matches(stack)) lock(slot);
 *
 * // A plain stack and the mode the setting declares.
 * ItemMatch.matches(config.target, ItemMatch.Match.CONTAINS, stack);
 * }</pre>
 *
 * <h3>The three modes</h3>
 *
 * <ul>
 *   <li>{@link Match#ITEM}: the kind of item only. Count and components are
 *       ignored: any diamond sword is a diamond sword.</li>
 *   <li>{@link Match#EXACT}: vanilla's own stack equality
 *       ({@link ItemStack#matches}): the same item, the same count, and exactly the
 *       same components. A rule with no components set is a <b>plain</b> item, so
 *       an enchanted or renamed one does not match it. The count matters for an
 *       item that stacks; one that does not is always one.</li>
 *   <li>{@link Match#CONTAINS}: the same item, and at least the components the
 *       rule sets; others may be there too, and the count is ignored. A rule with no
 *       components set matches <b>any variant</b> of the item. A component the rule
 *       removes ({@code !food} in {@code /give} syntax) must be absent.</li>
 * </ul>
 *
 * Whether an empty rule means "plain" or "any" is the mode's answer, never a guess:
 * {@code EXACT} says plain, {@code CONTAINS} says any.
 *
 * <h3>CONTAINS compares as vanilla's item predicates do</h3>
 *
 * Each component the rule sets is compared the way an item predicate in a command
 * or a datapack compares it ({@code /clear @s diamond_sword[damage=0]}, and the
 * partial {@code ~} form where vanilla has one):
 * <ul>
 *   <li>{@code enchantments} and {@code stored_enchantments}: the stack has at
 *       least the rule's enchantments, each at the rule's level (vanilla's
 *       {@link EnchantmentsPredicate}); it may have more.</li>
 *   <li>{@code custom_data}: the rule's tag is contained in the stack's (vanilla's
 *       NBT predicate, {@link CustomData#matchedBy}).</li>
 *   <li>Any other component: equal, as {@code DataComponentExactPredicate} compares
 *       it.</li>
 * </ul>
 *
 * <p>An empty rule ({@link ItemStack#EMPTY}, or air) matches nothing, and an empty
 * stack matches no rule: an empty picker slot is not a rule.
 *
 * <p>Common code: it reads only the stacks, so a mod's server side can use it too.
 * The record copies its stack; treat {@link #stack()} as read-only. Two rules are
 * equal when their modes are and their stacks match exactly ({@link ItemStack#matches}).
 *
 * @param stack the rule's stack: the item, and for {@code EXACT} and {@code CONTAINS}
 *              its count and components
 * @param match how a stack is compared with it
 */
public record ItemMatch(ItemStack stack, Match match) {

    /** How a stack is compared with a rule's. */
    public enum Match implements StringRepresentable {
        /** The kind of item only. */
        ITEM,
        /** The same item, count and components; no components set means a plain item. */
        EXACT,
        /** The same item and at least the components set; none set means any variant. */
        CONTAINS;

        /** Saved as {@code "item"}, {@code "exact"}, {@code "contains"}. */
        public static final Codec<Match> CODEC = StringRepresentable.fromEnum(Match::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Saves a rule as {@code {stack: <vanilla's stack>, match: "exact"}}. The stack
     * goes through vanilla's {@link ItemStack#OPTIONAL_CODEC}, so encode it with the
     * world's registry ops (enchantments are registry entries), as vanilla saves a
     * stack.
     */
    public static final Codec<ItemMatch> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(ItemMatch::stack),
            Match.CODEC.fieldOf("match").forGetter(ItemMatch::match)
    ).apply(i, ItemMatch::new));

    public ItemMatch {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(match, "match");
        stack = stack.copy();
    }

    /** Whether {@code candidate} matches this rule. */
    public boolean matches(ItemStack candidate) {
        return matches(stack, match, candidate);
    }

    /**
     * Whether {@code candidate} matches the rule {@code rule} under {@code match}:
     * the comparison {@link ItemMatch} describes, for a consumer that stores a plain
     * stack and declares the mode itself.
     */
    public static boolean matches(ItemStack rule, Match match, ItemStack candidate) {
        if (rule == null || candidate == null || rule.isEmpty() || candidate.isEmpty()) return false;
        if (!candidate.is(rule.getItem())) return false;
        return switch (match) {
            case ITEM -> true;
            case EXACT -> ItemStack.matches(rule, candidate);
            case CONTAINS -> containsComponents(rule, candidate);
        };
    }

    /**
     * Every component the rule's stack sets (its patch over the item's defaults) is on
     * the candidate, compared as vanilla's item predicates compare it; every component
     * the rule removes is absent.
     */
    private static boolean containsComponents(ItemStack rule, ItemStack candidate) {
        DataComponentPatch.SplitResult patch = rule.getComponentsPatch().split();
        // Removed on the rule (!type): the candidate must not have it either.
        for (DataComponentType<?> removed : patch.removed()) {
            if (candidate.has(removed)) return false;
        }
        for (TypedDataComponent<?> set : patch.added()) {
            DataComponentType<?> type = set.type();
            Object want = set.value();
            if (type == DataComponents.ENCHANTMENTS) {
                if (!enchantmentsPredicate((ItemEnchantments) want, false).matches(candidate)) return false;
            } else if (type == DataComponents.STORED_ENCHANTMENTS) {
                if (!enchantmentsPredicate((ItemEnchantments) want, true).matches(candidate)) return false;
            } else if (type == DataComponents.CUSTOM_DATA) {
                // The rule's tag contained in the stack's: vanilla's NBT predicate.
                CustomData data = candidate.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                if (!data.matchedBy(((CustomData) want).copyTag())) return false;
            } else if (!Objects.equals(want, candidate.get(type))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Vanilla's enchantments predicate (the {@code ~enchantments} of a command) for
     * the rule's enchantments: each one present at exactly its level.
     */
    private static EnchantmentsPredicate enchantmentsPredicate(ItemEnchantments want, boolean stored) {
        List<EnchantmentPredicate> each = new ArrayList<>();
        for (var entry : want.entrySet()) {
            each.add(new EnchantmentPredicate(Optional.of(HolderSet.direct(entry.getKey())),
                    MinMaxBounds.Ints.exactly(entry.getIntValue())));
        }
        return stored ? EnchantmentsPredicate.storedEnchantments(each) : EnchantmentsPredicate.enchantments(each);
    }

    // A record compares its stack by identity; a rule is a value.

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemMatch other && match == other.match && ItemStack.matches(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(stack) + 7 * stack.getCount() + match.hashCode();
    }

    @Override
    public String toString() {
        return "ItemMatch[" + stack + ", " + match + "]";
    }
}
