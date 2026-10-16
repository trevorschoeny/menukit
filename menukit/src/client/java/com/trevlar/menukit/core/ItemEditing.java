package com.trevlar.menukit.core;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The edits an item picker's Edit tab makes to a stack, as pure functions: each takes
 * a stack and returns a new one, never changing the one it was given. The name, the
 * enchantments (adapted to the item), the count, and every other component as text
 * in vanilla's {@code /give} syntax, read by vanilla's own item parser.
 */
@ApiStatus.Internal
public final class ItemEditing {

    private ItemEditing() {}

    // The name

    /** The stack's custom name as plain text, or "" for none. */
    public static String name(ItemStack stack) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : name.getString();
    }

    /** The stack named {@code text} (as an anvil names it), or with no custom name when blank. */
    public static ItemStack withName(ItemStack stack, String text) {
        ItemStack out = stack.copy();
        if (text.isBlank()) out.remove(DataComponents.CUSTOM_NAME);
        else out.set(DataComponents.CUSTOM_NAME, Component.literal(text));
        return out;
    }

    // Enchantments

    /**
     * One enchantment the Edit tab offers for a stack: its level on the stack (0 for
     * none), its highest level, and whether it is blocked, which is when it is not on
     * the stack and an enchantment that is on it excludes it (vanilla's exclusive
     * sets: Sharpness and Smite, Protection and Blast Protection).
     */
    public record EnchantChoice(Holder<Enchantment> enchantment, int level, int max, boolean blocked) {
        public Component name() {
            return enchantment.value().description();
        }
    }

    /**
     * The enchantments that fit the stack, by name: those that support the item, or
     * every one for an enchanted book (which stores them). Any already on the stack
     * are listed too, so none is hidden. Empty for an item nothing can enchant (a
     * stick): the Edit tab then shows no enchantments at all.
     */
    public static List<EnchantChoice> enchantChoices(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return List.of();
        DataComponentType<ItemEnchantments> type = EnchantmentHelper.getComponentType(stack);
        boolean stores = type == DataComponents.STORED_ENCHANTMENTS;
        ItemEnchantments on = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        List<EnchantChoice> out = new ArrayList<>();
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(e -> {
            int level = on.getLevel(e);
            if (!stores && level == 0 && !e.value().isSupportedItem(stack)) return;
            boolean blocked = false;
            if (level == 0) {
                for (Holder<Enchantment> other : on.keySet()) {
                    if (!Enchantment.areCompatible(other, e)) blocked = true;
                }
            }
            out.add(new EnchantChoice(e, level, e.value().getMaxLevel(), blocked));
        });
        out.sort(Comparator.comparing(c -> c.name().getString()));
        return out;
    }

    /**
     * The stack with {@code enchantment} at {@code level} (0 removes it), on the
     * component the item uses: stored enchantments on an enchanted book, its own
     * enchantments on anything else.
     */
    public static ItemStack withEnchantment(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemStack out = stack.copy();
        EnchantmentHelper.updateEnchantments(out, m -> m.set(enchantment, Math.max(0, level)));
        return out;
    }

    /** The component the enchantment list edits on this stack, when it shows one. */
    private static @Nullable DataComponentType<ItemEnchantments> listedEnchantments(ItemStack stack, HolderLookup.Provider registries) {
        return enchantChoices(stack, registries).isEmpty() ? null : EnchantmentHelper.getComponentType(stack);
    }

    // The count

    /** The count {@code text} gives this stack, or -1 when it is not a whole number from 1 to its stack size. */
    public static int parseCount(ItemStack stack, String text) {
        try {
            int n = Integer.parseInt(text.trim());
            return n >= 1 && n <= stack.getMaxStackSize() ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Every other component, in /give syntax

    /**
     * The stack's components that the tab does not edit with its own controls (the
     * name, and the enchantments while the list shows them), in vanilla's
     * {@code /give} syntax: {@code [unbreakable={},!food]}, ids without the
     * {@code minecraft:} prefix, in id order. "" when there are none.
     */
    public static String componentsText(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return "";
        Set<DataComponentType<?>> own = ownControls(stack, registries);
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        DataComponentPatch.SplitResult patch = stack.getComponentsPatch().split();
        List<String> parts = new ArrayList<>();
        for (TypedDataComponent<?> c : patch.added()) {
            if (own.contains(c.type()) || c.type().isTransient()) continue;
            parts.add(id(c.type()) + "=" + encode(c, ops));
        }
        for (DataComponentType<?> removed : patch.removed()) {
            if (!own.contains(removed)) parts.add("!" + id(removed));
        }
        // Sorted by id, a removal by the id it removes, so the text is the same every time.
        parts.sort(Comparator.comparing(p -> p.startsWith("!") ? p.substring(1) : p));
        return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
    }

    /**
     * What typing {@code text} into the components box gives: the stack with exactly
     * those components, keeping its count and the components the tab edits with its
     * own controls (unless the text sets them), or an error as vanilla's item parser
     * reports it (as a command would show it, with where it went wrong). Empty text
     * clears every other component. Vanilla's strict check runs too, so a stack the
     * game would refuse (a stack size over its maximum) is an error.
     */
    public static Edit withComponentsText(ItemStack stack, String text, HolderLookup.Provider registries) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String trimmed = text.strip();
        StringReader reader = new StringReader(id + trimmed);
        reader.setCursor(id.length());
        try {
            if (!trimmed.isEmpty() && trimmed.charAt(0) != ItemParser.SYNTAX_START_COMPONENTS) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, "[");
            }
            reader.setCursor(0);
            ItemInput input = new ItemParser(registries).parse(reader);
            if (reader.canRead()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherExpectedArgumentSeparator().createWithContext(reader);
            }
            ItemStack out = input.createItemStack(stack.getCount());
            // The tab's own controls keep their values unless the text names them.
            DataComponentPatch.SplitResult typed = input.components().split();
            for (DataComponentType<?> t : ownControls(stack, registries)) {
                if (!typed.added().has(t) && !typed.removed().contains(t)) copy(stack, out, t);
            }
            return new Edit(out, null);
        } catch (CommandSyntaxException e) {
            return new Edit(null, error(e, id.length(), trimmed));
        }
    }

    /** A components edit: the new stack, or the error to show instead (red, as a command's). */
    public record Edit(@Nullable ItemStack stack, @Nullable Component error) {}

    /** The components the tab edits with its own controls, kept out of the text. */
    private static Set<DataComponentType<?>> ownControls(ItemStack stack, HolderLookup.Provider registries) {
        DataComponentType<ItemEnchantments> listed = listedEnchantments(stack, registries);
        return listed == null ? Set.of(DataComponents.CUSTOM_NAME) : Set.of(DataComponents.CUSTOM_NAME, listed);
    }

    /** A vanilla command's error: the message, then the text up to where it failed and vanilla's "<--[HERE]". */
    private static Component error(CommandSyntaxException e, int prefix, String text) {
        var out = ComponentUtils.fromMessage(e.getRawMessage()).copy().withStyle(ChatFormatting.RED);
        int at = Math.max(0, Math.min(text.length(), e.getCursor() - prefix));
        if (e.getCursor() >= 0) {
            String before = text.substring(Math.max(0, at - 10), at);
            out.append(Component.literal(": " + (at > 10 ? "..." : "") + before).withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }
        return out;
    }

    /** {@code type}'s value on {@code from} onto {@code to}, or its absence. */
    private static <T> void copy(ItemStack from, ItemStack to, DataComponentType<T> type) {
        T value = from.get(type);
        if (value == null) to.remove(type);
        else to.set(type, value);
    }

    private static String id(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id == null) return "?";
        return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    /** A component's value as SNBT, vanilla's tag text, which its item parser reads back. */
    private static <T> String encode(TypedDataComponent<T> c, RegistryOps<Tag> ops) {
        return c.type().codecOrThrow().encodeStart(ops, c.value()).getOrThrow().toString();
    }
}
