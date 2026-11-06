package com.trevlar.menukit.api.element;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.EnchantmentPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An item rule: an item, the fields a stack has to share with it, and a count for the
 * consumer. What an {@code ItemPicker} slot holds, and the one comparison every mod
 * uses, so none writes its own.
 *
 * <pre>{@code
 * // A rule the player set with an item picker, tested against a stack in play.
 * if (rule.matches(stack)) lock(slot);
 *
 * // A plain stack as the rule.
 * ItemMatch.matches(config.target, stack);
 * }</pre>
 *
 * <h3>Blank means not checked</h3>
 *
 * The item always counts. Past that, each field the rule fills in narrows the match,
 * and a field left blank is not looked at:
 * <ul>
 *   <li><b>Name:</b> a rule with a custom name matches only stacks with that name.
 *       Without one, any name (or none) matches.</li>
 *   <li><b>Enchantments</b> (and an enchanted book's stored ones): each one the rule
 *       lists must be on the stack at that level, or at any level for one in
 *       {@link #anyLevel()} (its level left blank). Others may be there too.</li>
 *   <li><b>Every other component</b> the rule sets (its patch over the item's
 *       defaults, the picker's {@code /give}-syntax box) must match, compared as
 *       vanilla's item predicates compare it: {@code custom_data} contained in the
 *       stack's, anything else equal. A component the rule removes ({@code !food})
 *       must be absent from the stack. Damage is a component like any other, so it is
 *       checked only when the rule lists it.</li>
 * </ul>
 * So a rule fresh from the picker's grid (a diamond sword) matches every diamond sword,
 * and a potion from the grid every potion of that kind.
 *
 * <h3>The count is the consumer's</h3>
 *
 * {@link #count()} is never part of the match. It is a number the consumer reads (a
 * reserved slot's cap), from the picker's count slider; {@link #NONE} when the player
 * set none (the slider's N/A). The rule's {@link #stack()} is always one item.
 *
 * <p>An empty rule ({@link ItemStack#EMPTY}, or air) matches nothing, and an empty
 * stack matches no rule: an empty picker slot is not a rule.
 *
 * <h3>Saved rules from MenuKit 7</h3>
 *
 * {@link #CODEC} reads the 7.x shape ({@code match}, {@code ignoring}, {@code ignore_count})
 * and converts it: {@code item} keeps the item alone, {@code contains} and {@code exact}
 * keep every field the stack filled in, less the ignored components, and the stack's count
 * becomes {@link #count()}. One difference stays: an {@code exact} rule refused a stack
 * with components the rule did not have (a plain sword refused an enchanted one), and now
 * those are allowed, since a blank field is not checked.
 *
 * <h3>Any level</h3>
 *
 * {@link #anyLevel()} names enchantments whose level is left blank: the stack needs
 * each one at some level. The rule's stack holds them too, at a level that is not read
 * (the picker puts them on at I), so the rule's item draws enchanted and an enchantment
 * that conflicts with one of them is still kept off it. One named there that the stack
 * does not hold still counts.
 *
 * <p>Common code: it reads only the stacks, so a mod's server side can use it too.
 * The record copies its stack; treat {@link #stack()} as read-only. Two rules are equal
 * when their stacks have the same item and components, their counts are equal and they
 * leave the same levels blank.
 *
 * @param stack the rule's item and the fields it fills in, one of it
 * @param count the consumer's number, never matched: 1 or more, or {@link #NONE}
 * @param anyLevel the enchantments the stack needs at any level (8.0.0), empty for none
 */
public record ItemMatch(ItemStack stack, int count, Set<Holder<Enchantment>> anyLevel) {

    /** {@link #count()} when none is set: the count slider's N/A. */
    public static final int NONE = 0;

    /**
     * Saves a rule as {@code {stack: <vanilla's stack>, count: 12}}, with
     * {@code any_level: ["minecraft:sharpness"]} when it leaves levels blank. The stack goes through
     * vanilla's {@link ItemStack#OPTIONAL_CODEC}, so encode it with the world's registry ops
     * (enchantments are registry entries), as vanilla saves a stack. The count is its own
     * field, so it can be past a stack's 99. Reads MenuKit 7's rules too (see the class
     * description).
     */
    public static final Codec<ItemMatch> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(Saved::stack),
            Codec.INT.optionalFieldOf("count").forGetter(Saved::count),
            Enchantment.CODEC.listOf().optionalFieldOf("any_level", List.of()).forGetter(Saved::anyLevel),
            Codec.STRING.optionalFieldOf("match").forGetter(Saved::match),
            DataComponentType.CODEC.listOf().optionalFieldOf("ignoring", List.of()).forGetter(Saved::ignoring)
    ).apply(i, Saved::new)).xmap(Saved::rule, Saved::of);

    public ItemMatch {
        Objects.requireNonNull(stack, "stack");
        if (count < 0) throw new IllegalArgumentException("count must be 1 or more, or ItemMatch.NONE, got " + count);
        stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        // Kept in the order given, so a saved rule writes back the same.
        anyLevel = stack.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(anyLevel));
    }

    /** A rule from {@code stack} and the consumer's {@code count}, every enchantment at its level. */
    public ItemMatch(ItemStack stack, int count) {
        this(stack, count, Set.of());
    }

    /** A rule from {@code stack}: its item and components, its count as the count. */
    public ItemMatch(ItemStack stack) {
        this(stack, Math.max(1, stack.getCount()));
    }

    /** This rule with the consumer's count {@code count}. */
    public ItemMatch withCount(int count) {
        return new ItemMatch(stack, count, anyLevel);
    }

    /** This rule needing {@code enchantments} at any level, in place of the ones it named. */
    public ItemMatch withAnyLevel(Set<Holder<Enchantment>> enchantments) {
        return new ItemMatch(stack, count, enchantments);
    }

    /** Whether {@code candidate} matches this rule. */
    public boolean matches(ItemStack candidate) {
        return matches(stack, anyLevel, candidate);
    }

    /**
     * Whether {@code candidate} matches {@code rule} by the rule {@link ItemMatch}
     * describes: the same item, and every field {@code rule} fills in. For a consumer
     * that stores a plain stack.
     */
    public static boolean matches(ItemStack rule, ItemStack candidate) {
        return matches(rule, Set.of(), candidate);
    }

    /** The match, with {@code anyLevel} the enchantments whose level is not read. */
    private static boolean matches(ItemStack rule, Set<Holder<Enchantment>> anyLevel, ItemStack candidate) {
        if (rule == null || candidate == null || rule.isEmpty() || candidate.isEmpty()) return false;
        if (!candidate.is(rule.getItem())) return false;
        DataComponentPatch.SplitResult patch = rule.getComponentsPatch().split();
        // Removed on the rule (!type): the candidate must not have it either.
        for (DataComponentType<?> removed : patch.removed()) {
            if (candidate.has(removed)) return false;
        }
        for (TypedDataComponent<?> set : patch.added()) {
            DataComponentType<?> type = set.type();
            Object want = set.value();
            if (type == DataComponents.ENCHANTMENTS) {
                if (!enchantmentsPredicate((ItemEnchantments) want, anyLevel, false).matches(candidate)) return false;
            } else if (type == DataComponents.STORED_ENCHANTMENTS) {
                if (!enchantmentsPredicate((ItemEnchantments) want, anyLevel, true).matches(candidate)) return false;
            } else if (type == DataComponents.CUSTOM_DATA) {
                // The rule's tag contained in the stack's: vanilla's NBT predicate.
                CustomData data = candidate.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                if (!data.matchedBy(((CustomData) want).copyTag())) return false;
            } else if (!Objects.equals(want, candidate.get(type))) {
                return false;
            }
        }
        // Any level: each one present at some level, on the component the rule's item
        // uses (stored on a book), whether or not the rule's stack holds it.
        if (!anyLevel.isEmpty()) {
            boolean stored = EnchantmentHelper.getComponentType(rule) == DataComponents.STORED_ENCHANTMENTS;
            if (!atAnyLevel(anyLevel, stored).matches(candidate)) return false;
        }
        return true;
    }

    /**
     * Vanilla's enchantments predicate (the {@code ~enchantments} of a command) for
     * the rule's enchantments: each one present at exactly its level, less those in
     * {@code anyLevel}, which {@link #atAnyLevel} checks.
     */
    private static EnchantmentsPredicate enchantmentsPredicate(ItemEnchantments want, Set<Holder<Enchantment>> anyLevel, boolean stored) {
        List<EnchantmentPredicate> each = new ArrayList<>();
        for (var entry : want.entrySet()) {
            if (anyLevel.contains(entry.getKey())) continue;   // checked once, at any level, after the patch
            each.add(new EnchantmentPredicate(Optional.of(HolderSet.direct(entry.getKey())),
                    MinMaxBounds.Ints.exactly(entry.getIntValue())));
        }
        return stored ? EnchantmentsPredicate.storedEnchantments(each) : EnchantmentsPredicate.enchantments(each);
    }

    /** Vanilla's enchantments predicate for {@code enchantments}: each one present at some level. */
    private static EnchantmentsPredicate atAnyLevel(Set<Holder<Enchantment>> enchantments, boolean stored) {
        List<EnchantmentPredicate> each = new ArrayList<>();
        for (Holder<Enchantment> e : enchantments) {
            each.add(new EnchantmentPredicate(Optional.of(HolderSet.direct(e)), MinMaxBounds.Ints.atLeast(1)));
        }
        return stored ? EnchantmentsPredicate.storedEnchantments(each) : EnchantmentsPredicate.enchantments(each);
    }

    /**
     * A rule as saved, this version's or MenuKit 7's. {@code match} and {@code ignoring}
     * are only ever read: a 7.x rule's mode and the components it left out of the
     * comparison. A 7.x {@code ignore_count} needs nothing, since no count is matched now.
     */
    private record Saved(ItemStack stack, Optional<Integer> count, List<Holder<Enchantment>> anyLevel,
                         Optional<String> match, List<DataComponentType<?>> ignoring) {

        static Saved of(ItemMatch rule) {
            return new Saved(rule.stack(), Optional.of(rule.count()), List.copyOf(rule.anyLevel()), Optional.empty(), List.of());
        }

        ItemMatch rule() {
            // A 7.x rule kept its count on the stack.
            int n = Math.max(0, count.orElse(Math.max(1, stack.getCount())));
            if (stack.isEmpty()) return new ItemMatch(ItemStack.EMPTY, n);
            // A 7.x "item" rule: the kind of item only, so no field filled in.
            if (match.filter("item"::equals).isPresent()) return new ItemMatch(new ItemStack(stack.getItem()), n);
            // "contains" and "exact": every field the stack filled in, less what the rule ignored.
            Set<DataComponentType<?>> ignored = Set.copyOf(ignoring);
            DataComponentPatch kept = stack.getComponentsPatch().forget(ignored::contains);
            return new ItemMatch(new ItemStack(stack.typeHolder(), 1, kept), n, new LinkedHashSet<>(anyLevel));
        }
    }

    // A record compares its stack by identity; a rule is a value.

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemMatch other && count == other.count && anyLevel.equals(other.anyLevel)
                && ItemStack.isSameItemSameComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ItemStack.hashItemAndComponents(stack) + count) + anyLevel.hashCode();
    }

    @Override
    public String toString() {
        return "ItemMatch[" + stack + ", count " + (count == NONE ? "none" : count)
                + (anyLevel.isEmpty() ? "" : ", any level " + anyLevel) + "]";
    }
}
