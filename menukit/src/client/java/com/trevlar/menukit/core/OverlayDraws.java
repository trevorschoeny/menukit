package com.trevlar.menukit.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws an element hands over to be drawn on top of the screen's panels: after the
 * overlay layer (the one dialogs draw in), in a stratum of its own, with no clip.
 * A {@code WidgetHost} built with {@code overflow()} uses it while its widget has a
 * popup open, so the popup shows over the rest of the screen rather than being cut
 * off at the widget's rectangle.
 *
 * <p>The frame's composition decides when a draw may be deferred: {@code LayerPlan}
 * opens it around each panel it draws ({@link #allow}), and closes it for a panel
 * under a modal it does not belong to, so a popup behind a dialog stays behind it.
 * Outside a composition (a HUD, a check with no screen) {@link #defer} refuses and
 * the element draws in place. {@code LayerPlan} runs the queue after the overlay
 * layer ({@link #flush}).
 *
 * <p>Render thread only.
 */
public final class OverlayDraws {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private OverlayDraws() {}

    private static boolean open = false;
    private static final List<Consumer<GuiGraphicsExtractor>> QUEUED = new ArrayList<>();

    /** Whether the panel being drawn now may defer draws to the top. */
    public static void allow(boolean allowed) {
        open = allowed;
    }

    /**
     * Queues {@code draw} for the end of the frame's overlay layer. Returns false
     * when nothing is composing a frame that will run it; the caller draws in place.
     */
    public static boolean defer(Consumer<GuiGraphicsExtractor> draw) {
        if (!open) return false;
        QUEUED.add(draw);
        return true;
    }

    /** Runs what was queued this frame, on top, and closes the queue. */
    public static void flush(GuiGraphicsExtractor graphics) {
        open = false;
        if (QUEUED.isEmpty()) return;
        List<Consumer<GuiGraphicsExtractor>> draws = List.copyOf(QUEUED);
        QUEUED.clear();
        // A stratum of its own: above every panel's text, which would otherwise draw
        // over anything in the stratum it shares.
        graphics.nextStratum();
        for (Consumer<GuiGraphicsExtractor> draw : draws) {
            try {
                draw.accept(graphics);
            } catch (RuntimeException e) {
                // A foreign widget's draw: log it and keep the frame going.
                LOGGER.error("[OverlayDraws] A deferred draw threw; skipped", e);
            }
        }
    }
}
