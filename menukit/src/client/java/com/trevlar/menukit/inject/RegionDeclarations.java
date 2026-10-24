package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the container region is declared as (7.4.0; {@link ContainerRegions} is the region
 * itself): each declared section, each kind of section made per live key and the live keys'
 * sections on the open screen, the screens that show the fitted frame, and the open screen's
 * sections in stacking order. Declared from client init; the live keys are kept on the render
 * thread.
 */
@ApiStatus.Internal
public final class RegionDeclarations {

    private RegionDeclarations() {}

    /**
     * One declared section ({@code ContainerRegion.section}). {@code texture} is the
     * container texture it stands for where its source has no block that gives one
     * ({@link RegionLooks#sectionTexture}; vanilla's chest by default): its slice of the list and
     * its slot frames take that texture's look under the active resource packs, or with
     * OptiGUI the texture its rules give the section's {@code source} (the open screen's,
     * without one). {@code title}, if any, is drawn above its content; {@code name} stands in
     * for a blank title while other sections show, ahead of its source's name ({@link RegionTitles#shownTitles}).
     * {@code onHiddenLanding}, if any, runs when a quick move lands in it while it is hidden ({@link RegionReveal}).
     */
    public record Declared(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen,
                           Identifier texture, @Nullable Supplier<Component> title, @Nullable Supplier<Component> name,
                           @Nullable Supplier<ContainerRegion.@Nullable Source> source, @Nullable Runnable onHiddenLanding) {}

    /** A section as placed: what it was declared as, and the element that holds it in the scroll area. */
    record Placed(Declared section, AbstractPanelElement holder) {}

    /** The sections declared at init, in stacking order. */
    private static final List<Placed> DECLARED = new CopyOnWriteArrayList<>();
    /**
     * The open screen's sections, in stacking order: the declared ones and one for each live
     * key of each {@link Family}. Every per-frame array the region keeps is indexed by it.
     */
    private static List<Placed> sections = List.of();

    /** The open screen's sections, in stacking order, each with the element that holds it. */
    static List<Placed> sections() {
        return sections;
    }

    /**
     * {@code ContainerRegion.Builder.declare}: adds a section in its place, and the
     * region's panels with the first one. No screen is open yet, so no live key either.
     */
    public static synchronized void declare(Declared section) {
        for (Placed p : DECLARED) {
            if (p.section().id().equals(section.id())) {
                throw new IllegalStateException("ContainerRegion section '" + section.id() + "' is already declared");
            }
        }
        DECLARED.add(new Placed(section, ContainerRegions.holderFor(section)));
        DECLARED.sort(Comparator.comparingInt((Placed p) -> p.section().order()).thenComparing(p -> p.section().id()));
        ContainerRegions.buildPanels();
        restack();
    }

    /**
     * One kind of section made per live key ({@code ContainerRegion.sections}). On a screen
     * {@code matcher} takes, {@code keys} is read once a frame; each key's section is made the
     * frame the key first appears and kept, the same elements, for as long as the key is there
     * on that screen. Keys are compared with {@code equals}; a repeated key makes one section,
     * the first.
     */
    public record Family(String kind, ScreenMatcher matcher, Function<AbstractContainerScreen<?>, @Nullable List<?>> keys,
                         Function<Object, Declared> section) {}

    private static final List<Family> FAMILIES = new CopyOnWriteArrayList<>();
    /** Each kind's live keys on the open screen, in the order its keys gave them, and each one's section ({@code null}: its making threw). */
    private static final Map<Family, Map<Object, @Nullable Placed>> LIVE = new IdentityHashMap<>();

    /** {@code ContainerRegion.Sections.declare}: adds a kind, and the region's panels with the first. */
    public static synchronized void declareFamily(Family family) {
        for (Family f : FAMILIES) {
            if (f.kind().equals(family.kind())) {
                throw new IllegalStateException("ContainerRegion sections '" + family.kind() + "' is already declared");
            }
        }
        FAMILIES.add(family);
        ContainerRegions.buildPanels();
    }

    /**
     * Each kind's keys on {@code screen} now (none off a container screen, or on one the kind
     * is not on): a key that is new makes its section, one that is gone takes it with it
     * ({@link LiveKeys}). Nothing is made, and nothing restacks, while every kind's keys are
     * the ones it had. Before anything else in the frame reads the sections.
     */
    static void syncLive(@Nullable Screen screen) {
        boolean changed = false;
        for (Family f : FAMILIES) {
            List<?> keys = null;
            if (screen instanceof AbstractContainerScreen<?> acs && f.matcher().matches(acs)) {
                keys = Guard.get(() -> f.keys().apply(acs), "keys", f.kind());
            }
            Map<Object, @Nullable Placed> now = LiveKeys.resync(LIVE.getOrDefault(f, Map.of()), keys, k -> {
                Declared d = f.section().apply(k);
                return new Placed(d, ContainerRegions.holderFor(d));
            }, "section", f.kind());
            if (now == null) continue;
            if (now.isEmpty()) LIVE.remove(f);
            else LIVE.put(f, now);
            changed = true;
        }
        if (changed) restack();
    }

    /** A new screen opened: the live keys' sections were the screen before's, and go with it. */
    static void clearLive() {
        if (LIVE.isEmpty()) return;
        LIVE.clear();
        restack();
    }

    /**
     * The open screen's sections again, from the declared ones and every live key: stacked
     * by order, then by id (a kind's sections by its kind, then in its keys' order). What
     * the region learned about each section that stays (where it was, where its slots sit)
     * follows it to its new place ({@link ContainerRegions#sectionsChanged}).
     */
    static void restack() {
        record Entry(Placed placed, int order, String base, int index) {}
        List<Entry> entries = new ArrayList<>();
        for (Placed p : DECLARED) entries.add(new Entry(p, p.section().order(), p.section().id(), 0));
        for (Family f : FAMILIES) {
            int i = 0;
            for (Placed p : LIVE.getOrDefault(f, Map.of()).values()) {
                if (p != null) entries.add(new Entry(p, p.section().order(), f.kind(), i));
                i++;
            }
        }
        entries.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::base).thenComparingInt(Entry::index));
        List<Placed> before = sections;
        List<Placed> next = new ArrayList<>(entries.size());
        for (Entry e : entries) next.add(e.placed());
        sections = List.copyOf(next);

        ContainerRegions.sectionsChanged(before);
    }

    /** One {@code ContainerRegion.fitted} declaration: the screens {@code matcher} takes, in {@code scope}'s game mode. */
    private record Fitted(ModeScope scope, ScreenMatcher matcher) {}

    private static final List<Fitted> FITTED = new CopyOnWriteArrayList<>();

    /** {@code ContainerRegion.fitted}: the screens {@code matcher} takes show the fitted frame in {@code scope}. */
    public static void declareFitted(ModeScope scope, ScreenMatcher matcher) {
        FITTED.add(new Fitted(Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(matcher, "matcher")));
    }

    /** Whether {@code screen} shows the fitted frame now: a declaration takes it in the player's mode. */
    static boolean fitted(@Nullable Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return false;
        for (Fitted f : FITTED) if (f.scope().covers(PanelHost.currentMode()) && f.matcher().matches(acs)) return true;
        return false;
    }

    /**
     * Whether any section, or kind of section, is declared for {@code screen}: the region's panel is put on it.
     * Never the player's own inventory, survival's or creative's: the region is for screens
     * that hold a container, and the inventory screens are the player's own (creative swaps
     * its slots on every tab, so no container list could stand there). Checked by class, not
     * by slot: the survival inventory's first slot is its crafting result, not the player's.
     * The after-init gate and both panels' adapters all ask here, so this one test keeps the
     * region off those screens entirely.
     */
    @VisibleForTesting
    public static boolean targets(AbstractContainerScreen<?> screen) {
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return false;
        for (Placed p : DECLARED) if (p.section().matcher().matches(screen)) return true;
        for (Family f : FAMILIES) if (f.matcher().matches(screen)) return true;
        return false;
    }

    /** The open screen's sections, in stacking order. */
    static List<Declared> declared() {
        List<Declared> out = new ArrayList<>(sections.size());
        for (Placed p : sections) out.add(p.section());
        return out;
    }

}
