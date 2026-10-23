package com.trevlar.menukit.core;

import com.google.common.base.Suppliers;
import com.trevlar.menukit.mixin.ChestSpecialRendererAccessor;
import com.trevlar.menukit.mixin.ShulkerBoxSpecialRendererAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The bookkeeping behind {@code ItemAnimation.OPEN_LID}: drawing a shulker box or chest item
 * with its lid part open.
 *
 * <h3>How</h3>
 *
 * Vanilla's shulker box and chest item models end in a special renderer that takes an
 * openness, fixed when the model is baked (0 for every item). While {@link #draw} records an
 * item, {@code MKItemOpennessMixin} gives that item's layer a copy of its renderer with this
 * draw's openness instead, its extents to match, and its model identity one more element,
 * so the GUI's item atlas keeps each openness apart. An open lid stands past the 16 px box,
 * so the item is marked oversized in the GUI and vanilla draws it through its oversized-item
 * path rather than clipped to an atlas cell. Everything else about the draw is vanilla's: the
 * model, the pack's texture, the lighting, the pose.
 *
 * <p>Openness is rounded to {@link #STEPS} steps, so an animation makes a bounded number of
 * renderer copies and atlas entries. Render thread only.
 */
@ApiStatus.Internal
public final class ItemOpenness {

    private ItemOpenness() {}

    /** Openness is rounded to 1/STEPS. */
    static final int STEPS = 64;

    /** The openness items recorded right now draw with; 0 when none. */
    private static float pending = 0f;

    /** One renderer copy with a given openness, and its extents. */
    public record Opened(SpecialModelRenderer<?> renderer, Supplier<Vector3fc[]> extents, Object identity) {}

    /** The model identity element of an opened item: which openness, so the atlas keeps each apart. */
    private record Identity(int step) {}

    /** Each base renderer's copies, by step; weak, so a resource reload's old renderers go. */
    private static final Map<SpecialModelRenderer<?>, Opened[]> COPIES = new WeakHashMap<>();

    /** Draws {@code stack} at {@code (x, y)} with any lid it has {@code openness} open (0 to 1). */
    public static void draw(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float openness) {
        int step = Math.round(Math.max(0f, Math.min(1f, openness)) * STEPS);
        if (step == 0) {
            g.item(stack, x, y);
            return;
        }
        float previous = pending;
        pending = (float) step / STEPS;
        try {
            g.item(stack, x, y);
        } finally {
            pending = previous;
        }
    }

    /**
     * The copy of {@code base} for the openness being drawn, or {@code null} when nothing is
     * being opened or {@code base} has no lid MenuKit knows.
     */
    public static @Nullable Opened opened(SpecialModelRenderer<?> base) {
        if (pending == 0f) return null;
        if (!(base instanceof ShulkerBoxSpecialRenderer) && !(base instanceof ChestSpecialRenderer)) return null;
        int step = Math.round(pending * STEPS);
        Opened[] copies = COPIES.computeIfAbsent(base, b -> new Opened[STEPS + 1]);
        if (copies[step] == null) copies[step] = copy(base, (float) step / STEPS, step);
        return copies[step];
    }

    private static Opened copy(SpecialModelRenderer<?> base, float openness, int step) {
        SpecialModelRenderer<?> renderer;
        if (base instanceof ShulkerBoxSpecialRenderer s) {
            ShulkerBoxSpecialRendererAccessor a = (ShulkerBoxSpecialRendererAccessor) s;
            renderer = new ShulkerBoxSpecialRenderer(a.mk$renderer(), openness, a.mk$sprite());
        } else {
            ChestSpecialRendererAccessor a = (ChestSpecialRendererAccessor) base;
            renderer = new ChestSpecialRenderer(a.mk$sprites(), a.mk$model(), a.mk$sprite(), openness);
        }
        // As SpecialModelWrapper works out its own: every point the open model reaches.
        SpecialModelRenderer<?> r = renderer;
        Supplier<Vector3fc[]> extents = Suppliers.memoize(() -> {
            Set<Vector3fc> points = new HashSet<>();
            r.getExtents(points::add);
            return points.toArray(new Vector3fc[0]);
        });
        return new Opened(renderer, extents, new Identity(step));
    }
}
