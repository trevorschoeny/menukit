package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where an item picker's game data comes from: the registries (items, enchantments,
 * components), and the creative inventory's search contents, the list of every item
 * with its variants. It reads data and never items in play: no inventory, no menu,
 * nothing sent.
 *
 * <p>In a world, both are the world's: its registries, and the search tab built
 * with the world's enabled features and the operator-items option, exactly as the
 * creative inventory builds it (and its search trees updated as the creative
 * inventory updates them, so the creative search keeps working). With no world
 * open (a settings screen on the title screen, MenuKit's build checks) they are
 * vanilla's own, loaded once from the vanilla data pack the way a new world loads
 * them: the static registries' tags, then the data-driven registries (enchantments
 * and the rest) with theirs.
 */
@ApiStatus.Internal
public final class ItemSources {

    private ItemSources() {}

    /** Vanilla's registries with no world open, loaded on first need. */
    private static HolderLookup.@Nullable Provider vanilla;

    /** The creative collection the entries were built from, and the features they were built for. */
    private static @Nullable Collection<ItemStack> builtFrom;
    private static FeatureFlagSet builtFor = FeatureFlags.DEFAULT_FLAGS;
    /** The entries, with variants [0] and the kinds only [1], each made on first need. */
    private static final List<ItemStack>[] ENTRIES = newLists();
    /** Each entry's search text, lower-cased, made on the first search. */
    private static final String[][] TEXTS = new String[2][];

    @SuppressWarnings("unchecked")
    private static List<ItemStack>[] newLists() {
        return (List<ItemStack>[]) new List[2];
    }

    /** The registries to parse, enchant and list items with: the world's, or vanilla's. */
    public static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) return mc.level.registryAccess();
        if (vanilla == null) vanilla = loadVanilla();
        return vanilla;
    }

    /**
     * Vanilla's registries from its data pack, as a world loads them (WorldLoader):
     * the static registries' tags first, then the data-driven registries against
     * them. Item tags are bound only if no tags are bound yet (before the first
     * world of a session), so a world's own tags are never replaced; they decide
     * which enchantments an item supports. Items get vanilla's components the same
     * way, when none are bound yet.
     */
    private static HolderLookup.Provider loadVanilla() {
        // Vanilla's own data pack, as a server reads it (server data, not client assets).
        ResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
        LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
        List<Registry.PendingTags<?>> staticTags =
                TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
        if (Items.DIAMOND_SWORD.builtInRegistryHolder().tags().findAny().isEmpty()) {
            staticTags.forEach(Registry.PendingTags::apply);
        }
        RegistryAccess.Frozen context = layers.getAccessForLoading(RegistryLayer.WORLD);
        List<HolderLookup.RegistryLookup<?>> contextLookups = TagLoader.buildUpdatedLookups(context, staticTags);
        RegistryAccess.Frozen world = RegistryDataLoader.load(resources, contextLookups,
                RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
        HolderLookup.Provider provider = HolderLookup.Provider.create(Stream.concat(contextLookups.stream(), world.listRegistries()));
        // Items have no components until a world binds them (a stack cannot even be
        // made before): bind vanilla's, as a world load does, if none are bound yet.
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
        }
        return provider;
    }

    /**
     * Makes sure item stacks can be made: before the first world of a session items
     * have no components, so this loads vanilla's data as {@link #registries} does.
     * Cheap once done; a world open, it does nothing.
     */
    public static void ready() {
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) registries();
    }

    /**
     * Every item, as the item picker's grid lists it: the creative inventory's search
     * tab first, in its order, each variant its own entry (every potion and tipped
     * arrow, each enchanted book level, goat horn, firework duration, ominous bottle),
     * then any enabled item no creative tab shows. With {@code kindsOnly}, each item
     * once, plain, in the same order. Built as the creative inventory builds it,
     * when it is not built yet or is stale.
     */
    public static List<ItemStack> entries(boolean kindsOnly) {
        Collection<ItemStack> from = creativeSearch();
        if (from != builtFrom) {
            builtFrom = from;
            ENTRIES[0] = ENTRIES[1] = null;
            TEXTS[0] = TEXTS[1] = null;
        }
        int k = kindsOnly ? 1 : 0;
        if (ENTRIES[k] == null) {
            List<ItemStack> out = new ArrayList<>();
            Set<Item> seen = new HashSet<>();
            for (ItemStack stack : from) {
                if (seen.add(stack.getItem())) out.add(kindsOnly ? new ItemStack(stack.getItem()) : stack);
                else if (!kindsOnly) out.add(stack);
            }
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && item.isEnabled(builtFor) && seen.add(item)) out.add(new ItemStack(item));
            }
            ENTRIES[k] = List.copyOf(out);
        }
        return ENTRIES[k];
    }

    /** The creative search tab's contents, built for the world (or vanilla's defaults) first. */
    private static Collection<ItemStack> creativeSearch() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player != null) {
            HolderLookup.Provider holders = player.level().registryAccess();
            boolean ops = player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
            builtFor = player.connection.enabledFeatures();
            if (CreativeModeTabs.tryRebuildTabContents(builtFor, ops, holders)) {
                // What the creative inventory does after a rebuild, so its own search
                // sees the same contents (it skips this when it finds them built).
                List<ItemStack> items = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
                player.connection.searchTrees().updateCreativeTooltips(holders, items);
                player.connection.searchTrees().updateCreativeTags(items);
            }
        } else if (CreativeModeTabs.searchTab().getDisplayItems().isEmpty()) {
            builtFor = FeatureFlags.DEFAULT_FLAGS;
            CreativeModeTabs.tryRebuildTabContents(builtFor, false, registries());
        }
        return CreativeModeTabs.searchTab().getDisplayItems();
    }

    /**
     * The {@link #entries} whose search text has every word of {@code query}: the
     * name, the id and the tooltip (so an enchanted book is found by its
     * enchantment, a potion by its effect). All of them for a blank query.
     */
    public static List<ItemStack> search(boolean kindsOnly, String query) {
        List<ItemStack> all = entries(kindsOnly);
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) return all;
        int k = kindsOnly ? 1 : 0;
        if (TEXTS[k] == null) TEXTS[k] = texts(all);
        String[] text = TEXTS[k];
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            boolean every = true;
            for (String w : words) {
                if (!w.isEmpty() && !text[i].contains(w)) {
                    every = false;
                    break;
                }
            }
            if (every) out.add(all.get(i));
        }
        return out;
    }

    private static String[] texts(List<ItemStack> list) {
        Item.TooltipContext context = Item.TooltipContext.of(registries());
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            StringBuilder b = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            for (var line : stack.getTooltipLines(context, null, TooltipFlag.NORMAL)) b.append('\n').append(line.getString());
            out[i] = b.toString().toLowerCase(Locale.ROOT);
        }
        return out;
    }
}
