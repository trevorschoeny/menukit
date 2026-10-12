package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A rectangle a library names and supplies per screen, for panels to be placed around
 * with an {@link AnchorPanelAdapter}: a slot group's box (SlotKit), the pause menu's
 * button column, the title screen's buttons. MenuKit knows nothing about what the
 * rectangle is; it asks {@link #box} for it every frame on every open screen.
 *
 * <pre>{@code
 * public static final Anchor PAUSE_BUTTONS = Anchor.of(
 *         Identifier.fromNamespaceAndPath("mymod", "pause_buttons"),
 *         screen -> screen instanceof PauseScreen ? buttonColumn(screen) : null);
 * }</pre>
 *
 * <h3>The box</h3>
 * Called once per frame per open screen, so it should be a lookup, not a search. It
 * returns the rectangle in screen coordinates, or {@code null} when the anchor is not
 * on that screen right now (the wrong screen, or the thing it names is hidden): its
 * panels then take no place and claim nothing. It may be asked about any screen, a
 * container screen or not.
 *
 * <h3>One anchor per id</h3>
 * An id names one rectangle. Declare each {@code Anchor} once, keep it, and pass it to
 * every adapter that places against it: panels on one anchor share one host and stack
 * in its regions. A second {@code Anchor.of} with an id already declared throws.
 * Anchors are declarations: they freeze with MenuKit's others at client start.
 */
public final class Anchor {

    private static final Map<Identifier, Anchor> DECLARED = new ConcurrentHashMap<>();

    private final Identifier id;
    private final Function<Screen, @Nullable Reference> box;

    private Anchor(Identifier id, Function<Screen, @Nullable Reference> box) {
        this.id = id;
        this.box = box;
    }

    /**
     * Declares an anchor.
     *
     * @param id  the anchor's id, namespaced by the declaring mod
     * @param box the rectangle on a screen this frame, or {@code null} when the anchor
     *            is not on that screen
     * @throws IllegalStateException if {@code id} is already declared, or after
     *                               MenuKit's declarations froze
     */
    public static Anchor of(Identifier id, Function<Screen, @Nullable Reference> box) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(box, "box");
        Declarations.requireOpen("Anchor.of(" + id + ")");
        Anchor anchor = new Anchor(id, box);
        if (DECLARED.putIfAbsent(id, anchor) != null) {
            throw new IllegalStateException("Anchor " + id + " is already declared. "
                    + "Declare each anchor once and reuse it.");
        }
        return anchor;
    }

    /** The anchor's id. */
    public Identifier id() { return id; }

    /** The anchor's rectangle on {@code screen} this frame, or {@code null} when it is not there. */
    public @Nullable Reference box(Screen screen) {
        return box.apply(screen);
    }

    @Override
    public String toString() {
        return "Anchor[" + id + "]";
    }
}
