package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.menukit.inject.RegionLayout;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The container region (7.4.0): on a container screen, the player's inventory never
 * scrolls, and everything above it is one column that scrolls together.
 * A chest, a shulker box opened below it and another one below that are one region,
 * never a scroll area each. Mods put their containers' slot elements in it as
 * <b>sections</b>, stacked top to bottom; there is one region per screen, however many
 * mods add to it. It is never on the player's own inventory, survival's
 * ({@code InventoryScreen}) or creative's ({@code CreativeModeInventoryScreen}), whatever a
 * section's targeting says.
 *
 * <pre>{@code
 * ContainerRegion.section("mymod:opened-shulker")
 *         .title(() -> Component.translatable("container.shulkerBox"))
 *         .source(() -> ContainerRegion.Source.of(session.box()))   // its texture and its name: "Lime Shulker Box"
 *         .content(shulkerGrid)            // SlotKit's slot elements, say
 *         .on(ContainerScreen.class)
 *         .order(10)                       // below the chest's own section (order 0)
 *         .visibleWhen(() -> session.isOpen())
 *         .declare();
 * }</pre>
 *
 * <h3>Two panels</h3>
 * The containers and the inventory are two panels, each with its own frame: the
 * <b>container list</b> on top, holding every section with its scrollbar inside its
 * frame, and the <b>inventory panel</b> below it, MenuKit's gap between panels apart. The
 * inventory panel is around the player's inventory, and moves only as the list grows.
 * Scrolled content clips at the container list's own bottom edge.
 *
 * <p>Both are the screen's background ({@link Panel.Builder#background()}): drawn where
 * the screen draws its own image, under everything else on the screen, vanilla's labels
 * and slots and every other mod's widgets included, and claiming nothing, so another
 * mod's button on either panel stays live and gets its clicks first. The region's own
 * slots draw with the list, right after it, so another mod's button over one covers its
 * item; they are cut at the list's edges.
 *
 * <h3>The screen's bounds</h3>
 * The screen's {@code leftPos}, {@code topPos}, {@code imageWidth} and {@code imageHeight}
 * are the rectangle around both panels, scrollbar lane included, set whenever the layout
 * changes (the screen opening, a resize, a section opening or closing), never on a
 * scroll. So a panel placed on the menu frame, another mod's buttons by the screen's
 * edges and a recipe viewer keeping clear of the screen all see both panels, and a click
 * in the gap between them with an item held is inside the screen, so it drops nothing.
 * Vanilla's title is moved back by as much as the origin moved, so it stays put on
 * screen, and the player's inventory slots and "Inventory" label go with the inventory
 * panel. The slots are placed every frame from the slots the menu holds then, so one another
 * mod puts in while the screen is open goes with the panel too, and putting the bounds back
 * moves exactly the slots that were moved.
 *
 * <p>When the layout changes while the screen is open (a section opening or closing, the
 * list starting to scroll), the screen is laid out again the way a window resize does
 * ({@code Screen.resize} at its own size): every mod adds its widgets again from its own
 * init hook, at the new bounds, so a button another mod placed by the screen's edges
 * follows the panel it was by. Only then, never on a scroll, and once a frame however
 * many sections changed. What a resize keeps is kept (the item on the cursor, the scroll,
 * which sections are open, the anvil's name and the creative search); what it drops is
 * dropped (which widget had focus). While the region is up, the
 * screen's own background still runs, every mod's hook in it included, but vanilla's own
 * container texture and the screen's title are left out: the two panels and the sections'
 * titles are in their place.
 *
 * <h3>Anchors</h3>
 * To put a button by one panel rather than by the whole screen, place it on
 * {@link #CONTAINERS} (the container list) or {@link #INVENTORY} (the inventory panel)
 * with an {@link AnchorPanelAdapter}. Either is absent on a screen the region is not on.
 *
 * <pre>{@code
 * Panel sort = Panel.builder("mymod:sort_chest").add(sortButton)
 *         .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP)).build();
 * new AnchorPanelAdapter(sort).on(ContainerRegion.CONTAINERS);
 * }</pre>
 *
 * <h3>What the region does</h3>
 * <ul>
 *   <li><b>Grows, centred, then scrolls.</b> The container list starts at the screen's
 *       own container part, where vanilla's is, and grows with its sections. While the
 *       two panels fit, they stay centred on the screen as one block: the list's top
 *       moves up and the inventory panel down by half the growth each. Where one side
 *       has less room (a window too short for vanilla's own margins), that side holds and
 *       the other moves until the block is centred. Only once the block reaches the top
 *       and bottom margins does it scroll. Each panel moves at most a pixel per pixel of
 *       growth, so nothing jumps. It is never shorter than the screen's own container
 *       part, so a tiny window still shows vanilla's height, scrolling. A resize lays it
 *       out again.</li>
 *   <li><b>The wheel, last, from anywhere on the screen.</b> It scrolls the region
 *       only when vanilla and every other handler declined the turn: over a slot, a
 *       mod that moves items with the wheel (Mouse Tweaks, Inventory Profiles Next)
 *       keeps it, and a recipe viewer's list keeps its own; anywhere nothing wants it,
 *       the region scrolls. Nothing detects any mod.</li>
 *   <li><b>A shift-click lands in view.</b> A quick move into a slot of the region
 *       that is scrolled out of view scrolls it there. A quick move out of it (into the
 *       inventory) scrolls nothing.</li>
 *   <li><b>Carrying an item to an edge scrolls it</b>, faster nearer the edge.</li>
 *   <li><b>Smooth.</b> Pixels, never whole rows; a slot across the edge is drawn, hovered
 *       and clicked only where it shows ({@code SlotClip}).</li>
 *   <li><b>MenuKit's scrollbar</b>, in the screen's look, inside the container list's
 *       frame at its right while anything scrolls.</li>
 * </ul>
 *
 * <h3>A section</h3>
 * Its {@link Builder#title} is drawn where vanilla draws a container's, and its
 * {@link Builder#content} below it, at the region's content edge, where vanilla's grid
 * frame is: a nine-wide grid at {@code (0, 0)} lines up with vanilla's own chest. Without a
 * title the content starts at the top and draws whatever it likes there. A section draws
 * its own slot frames. Hidden sections take no room. The screen's own container is a
 * section too: whoever puts the region on a screen presents that screen's grid in it
 * (SlotKit's {@code SlotElement.slot(Address)}), since what is behind the container list is
 * covered.
 *
 * <p>A section shows while its own {@link Builder#visibleWhen} holds <b>and its content is
 * visible</b>: content that hides itself takes its section with it. A slot library's element
 * for a slot group that is not on the open menu, or whose slots are all hidden, hides
 * itself, so a section of such a group needs no condition of its own.
 *
 * <h3>Resource packs</h3>
 * The two panels follow the resource pack's container art, as vanilla's own screen
 * would under it: the container list takes each section's container texture, the
 * inventory panel the inventory half of the screen's own. A section's container texture is
 * its {@link Builder#source}'s block's (a shulker box's for a shulker box, the chest's for a
 * chest, ender chest or barrel), else its {@link Builder#texture} (vanilla's chest by
 * default): its slot frames and its part of the list take that texture's look, down from a pixel above it to the next
 * section's, the list's top and bottom with the first and last. It scrolls with the
 * section. A section whose fill differs from the first one's, a block of its own, has its
 * content moved down 2 px, so its title sits inside the block as vanilla's sits inside its
 * art. MenuKit adds no colour of its own: a section's colour is the pack's. Text on the
 * list keeps vanilla's colours wherever they read on the pack's fill, and takes one made
 * from the fill where they would not.
 *
 * <h3>Each section a slice of its own container's screen</h3>
 * Each section's part of the list, from its band's top to its bottom, is a slice of its
 * own texture's panel: the list's left and right edges there are that texture's, and so is
 * its fill; the list's top is the first section's, its bottom the last's. The outline runs
 * on unbroken; where two sections meet, the colours change. With no pack every
 * texture is vanilla's grey, and the list is MenuKit's panel as always.
 *
 * <h3>Containers whose art a pack picks by block (OptiGUI)</h3>
 * With OptiGUI installed, a pack's rules can give a container art of its own by its block
 * (a lime box), its name ("Ores"), its biome or its height. OptiGUI picks that for a whole
 * screen, from the block that opened it; the region asks for each section's own. A section
 * that knows its container says so with {@link Builder#source}, from the block entity it
 * shows or the item it was opened from ({@link Source#of(BlockEntity)},
 * {@link Source#of(ItemStack)}). One that doesn't is the screen's own container: it takes
 * the block the screen was opened on (the one under the crosshair as it opened, when it
 * holds a container the screen's size and the screen has its title), else what OptiGUI
 * picked for the screen, as the inventory panel does. Without OptiGUI, no rules are read.
 *
 * <h3>Titles and names</h3>
 * Title a section that is a vanilla container's with vanilla's translation key
 * ({@code Component.translatable("container.shulkerBox")}, or the screen's own title,
 * which is one) rather than a literal: packs whose art covers the title strip blank those
 * keys, and with one container on the screen the section's title goes blank with vanilla's.
 * With two or more sections shown, the player has to tell them apart, so a section whose
 * title comes out blank shows a name instead, one packs don't blank: its {@link Builder#name}
 * if it has one; else its container's, from its {@link Builder#source} (or, for the screen's
 * own container, the block it was opened on): the name the player gave it, else its block's
 * name in the player's language ("Lime Shulker Box"); else what vanilla's own English says
 * for the title's key ("Shulker Box"). Sections that would show the same name are numbered
 * ("Lime Shulker Box 1", "Lime Shulker Box 2"). The first section's title moves right,
 * clear of the pack's top corner, where the pack decorates it.
 *
 * <p>Declare from your client initializer: sections freeze with every other declaration.
 */
public final class ContainerRegion {

    private ContainerRegion() {}

    /** The room between two sections, and from a section's grid to vanilla's "Inventory" below. */
    public static final int GAP = RegionLayout.GAP;

    /** The width a section has before the scrollbar: nine slots. */
    public static final int CONTENT_WIDTH = RegionLayout.CONTENT_WIDTH;

    /**
     * The container list's rectangle on a screen the region is on, scrollbar lane
     * included while it scrolls: for buttons that act on the containers.
     */
    public static final Anchor CONTAINERS = ContainerRegions.CONTAINERS;

    /** The inventory panel's rectangle on a screen the region is on: for buttons that act on the inventory. */
    public static final Anchor INVENTORY = ContainerRegions.INVENTORY;

    /** Vanilla's chest texture: the default look of a section, and of the list on a chest's screen. */
    public static final Identifier CHEST = PackLook.CHEST;

    /** Vanilla's shulker box texture: for a section that shows a shulker box. */
    public static final Identifier SHULKER_BOX = PackLook.SHULKER_BOX;

    /**
     * Where a section's items are: the container it shows. Its block's id ({@code null} for a
     * container with no block behind it, such as one opened from an item that isn't a block),
     * its custom name ({@code null} for none), and where it is ({@code null} for a container
     * opened from an item: the player's position). The section takes its texture and its name
     * from it, and with OptiGUI the pack's rules are evaluated against it: by block, name and
     * place, or with no block by name and place only.
     */
    public record Source(@Nullable Identifier block, @Nullable String customName, @Nullable BlockPos pos) {

        /** A placed container: its block, its custom name, its position. */
        public static Source of(BlockEntity container) {
            String name = container instanceof Nameable n && n.hasCustomName() ? n.getCustomName().getString() : null;
            return new Source(BuiltInRegistries.BLOCK.getKey(container.getBlockState().getBlock()), name, container.getBlockPos());
        }

        /**
         * A container opened from its item (a shulker box in the inventory): the item's block,
         * so its dye, and the name the player gave it. Where it is, the player is. An item that
         * isn't a block (a backpack) gives no block.
         */
        public static Source of(ItemStack item) {
            Identifier block = item.getItem() instanceof BlockItem b ? BuiltInRegistries.BLOCK.getKey(b.getBlock()) : null;
            Component name = item.get(DataComponents.CUSTOM_NAME);
            return new Source(block, name == null ? null : name.getString(), null);
        }
    }

    /** Starts a section, its id namespaced and unique ({@code "mymod:opened-shulker"}). */
    public static Builder section(String id) {
        return new Builder(id);
    }

    /** Builds one section of the container region. {@link #content} is required. */
    public static final class Builder {
        private final String id;
        private PanelElement content;
        private ScreenMatcher matcher = ScreenMatcher.all();
        private boolean targeted = false;
        private int order = 0;
        private BooleanSupplier visibleWhen = () -> true;
        private Identifier texture = CHEST;
        private @Nullable Supplier<Component> title, name;
        private @Nullable Supplier<@Nullable Source> source;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Required: what the section shows, placed from the region's content edge. */
        public Builder content(PanelElement content) {
            this.content = Objects.requireNonNull(content, "content");
            return this;
        }

        /** The screens it is on. Without {@code on} or {@link #onMatching}, every container screen. */
        @SafeVarargs
        public final Builder on(Class<? extends AbstractContainerScreen<?>>... screens) {
            if (screens.length == 0) {
                throw new IllegalArgumentException("ContainerRegion section '" + id + "': on() needs at least one screen class");
            }
            return onMatching(ScreenMatcher.only(screens));
        }

        /** The screens it is on, by a matcher. */
        public Builder onMatching(ScreenMatcher matcher) {
            if (targeted) throw new IllegalStateException("ContainerRegion section '" + id + "': targeting declared twice");
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.targeted = true;
            return this;
        }

        /** Where it stacks: lower is higher up; ties go by id. Default 0. */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** Shown while this holds, read every frame. Hidden, it takes no room. */
        public Builder visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * The section's title, read every frame, drawn where vanilla draws a container's
         * (1 px in, 11 px above the content). Vanilla's key for a vanilla container
         * ({@code Component.translatable("container.shulkerBox")}), so a pack that blanks
         * vanilla's titles blanks it too (see "Titles and names").
         */
        public Builder title(Supplier<Component> title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /**
         * What the section is called while its {@link #title} comes out blank and other
         * sections show, read every frame, in place of its container's own name. Only needed
         * where that falls short: a section's {@link #source} names it already (the name the
         * player gave it, else its block's, "Lime Shulker Box"), and a section with no source is
         * called by the block the screen was opened on. With neither, vanilla's own English for
         * the title's key.
         */
        public Builder name(Supplier<Component> name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * The container texture this section stands for where no block says so, a full
         * texture path ({@link #CHEST}, {@link #SHULKER_BOX}, or a mod's own container texture
         * laid out like vanilla's): under a resource pack that restyles it, the section's slot
         * frames and its part of the list take its look. Vanilla's chest by default. A section
         * with a {@link #source} takes its block's texture instead (a shulker box's for a
         * shulker box, the chest's for a chest, ender chest or barrel), so the two can't
         * disagree; this is for a section with no block behind it (a mod's own slot group, a
         * source from an item that isn't a block, a block of another kind) and for the screen's
         * own container. In that order: the source's block, then this, then the chest.
         */
        public Builder texture(Identifier texture) {
            this.texture = Objects.requireNonNull(texture, "texture");
            return this;
        }

        /**
         * The container this section shows, read every frame. The section takes its texture
         * from its block (see {@link #texture}) and its name from it (see {@link #name}), and
         * with OptiGUI installed the pack's rules are evaluated against it, so a lime box's
         * section has the lime art whatever screen it is on. Without a source, the section is
         * the screen's own container: the block the screen was opened on, else what OptiGUI
         * picked for the open screen. A source that gives {@code null} (its container not known
         * this frame) takes its {@link #texture} and what OptiGUI picked for the open screen.
         */
        public Builder source(Supplier<@Nullable Source> source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        /**
         * Declares the section.
         *
         * @throws IllegalStateException after declarations froze, or when {@code id} is
         *         already declared or no content was given
         */
        public void declare() {
            Declarations.requireOpen("ContainerRegion.section(" + id + ")");
            if (content == null) throw new IllegalStateException("ContainerRegion section '" + id + "': content(...) is required");
            ContainerRegions.declare(new ContainerRegions.Declared(id, content, matcher, order, visibleWhen, texture, title, name, source));
        }
    }
}
