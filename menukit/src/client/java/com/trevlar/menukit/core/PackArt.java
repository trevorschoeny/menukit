package com.trevlar.menukit.core;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Optional;

/**
 * What the active resource packs supply, read the same way by every look that follows
 * them ({@link PackLook} for panels, {@link ControlLook} for controls, {@link PackText} for
 * text): whose a resource is, the color a sprite's face shows, and the one luminance
 * formula colors are compared with.
 */
@ApiStatus.Internal
public final class PackArt {

    private PackArt() {}

    /** The pack ids of vanilla's own resources and of MenuKit's own (a Fabric mod's pack is its mod id). */
    public static final String VANILLA_PACK = "vanilla", MENUKIT_PACK = "menukit";

    /** Whether a resource is present and supplied by a pack other than {@code own}, its owner's. */
    public static boolean fromPack(Optional<Resource> res, String own) {
        return res.isPresent() && !own.equals(res.get().sourcePackId());
    }

    /**
     * The color a sprite's face shows, opaque, or {@code null} when it can't be read
     * (nothing there, a broken file, an all-transparent face). The face is the part of the
     * sprite a control or panel stretches over most of its area: for a nine-sliced sprite
     * its center slice; for any other, the middle half each way. Only the first frame is read
     * (an animated sprite stacks its frames down the file). Each pixel counts by its alpha,
     * so a face with see-through gaps averages what does show. Never throws.
     */
    public static @Nullable Integer faceColor(Resource res) {
        try (InputStream in = res.open(); NativeImage img = NativeImage.read(in)) {
            ResourceMetadata meta = res.metadata();
            int w = img.getWidth(), h = img.getHeight();
            FrameSize frame = meta.getSection(AnimationMetadataSection.TYPE)
                    .map(a -> a.calculateFrameSize(w, h)).orElse(new FrameSize(w, h));
            int fw = Math.min(w, frame.width()), fh = Math.min(h, frame.height());
            int[] area = faceArea(fw, fh, meta.getSection(GuiMetadataSection.TYPE).map(GuiMetadataSection::scaling).orElse(null));
            return average(img, area);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The face of a {@code fw x fh} frame, {@code [x0, y0, x1, y1]}: a nine-slice's center
     * (its borders are in the sprite's own units, so scaled to the file's pixels), else the
     * middle half; the center pixel where that comes out empty (a nine-slice all border).
     */
    static int[] faceArea(int fw, int fh, @Nullable GuiSpriteScaling scaling) {
        int x0, y0, x1, y1;
        if (scaling instanceof GuiSpriteScaling.NineSlice n && n.width() > 0 && n.height() > 0) {
            double sx = fw / (double) n.width(), sy = fh / (double) n.height();
            x0 = (int) Math.round(n.border().left() * sx);
            x1 = fw - (int) Math.round(n.border().right() * sx);
            y0 = (int) Math.round(n.border().top() * sy);
            y1 = fh - (int) Math.round(n.border().bottom() * sy);
        } else {
            x0 = fw / 4;
            x1 = fw - fw / 4;
            y0 = fh / 4;
            y1 = fh - fh / 4;
        }
        if (x0 >= x1 || y0 >= y1) return new int[]{fw / 2, fh / 2, fw / 2 + 1, fh / 2 + 1};
        return new int[]{x0, y0, x1, y1};
    }

    /** The alpha-weighted average color of {@code area} of {@code img}, opaque; {@code null} if nothing in it shows. */
    private static @Nullable Integer average(NativeImage img, int[] area) {
        long a = 0, r = 0, g = 0, b = 0;
        for (int y = area[1]; y < area[3]; y++) {
            for (int x = area[0]; x < area[2]; x++) {
                int p = img.getPixel(x, y), pa = p >>> 24;
                a += pa;
                r += (long) pa * ((p >> 16) & 0xFF);
                g += (long) pa * ((p >> 8) & 0xFF);
                b += (long) pa * (p & 0xFF);
            }
        }
        if (a == 0) return null;
        return 0xFF000000 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
    }

    /** A color's relative luminance, 0 to 1 (WCAG's, from linear light): how light it reads. */
    public static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 0xFF) + 0.7152 * channel((argb >> 8) & 0xFF) + 0.0722 * channel(argb & 0xFF);
    }

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
