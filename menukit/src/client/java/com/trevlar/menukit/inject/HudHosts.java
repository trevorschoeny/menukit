package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.RegionMath;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The HUD's two hosts: what {@code MK.registerHud} registers HUD panels into, the HUD draws,
 * and the HUD edit screen moves panels in ({@link PanelEditing}).
 */
@ApiStatus.Internal
public final class HudHosts {

    private HudHosts() {}

    /**
     * The window's: each panel on its InsideRegion spot of the game window with the HUD's
     * insets, or in an OutsideRegion around the vanilla hotbar ({@link PanelHost#hudFrame}).
     * Registered at init, frozen at client start, drawn in sorted (priority, modId, sequence)
     * order.
     */
    public static final PanelHost WINDOW = PanelHost.hud(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        return PanelHost.hudFrame(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                drawnOffhandSide(mc));
    });

    /**
     * The status column's: panels stacked up from just above the XP level number, in the gap
     * between the hearts and the hunger bar ({@code HudPanel.Builder.statusColumn}). A host of
     * its own, because its reference is the column's base, not the hotbar.
     */
    public static final PanelHost STATUS = PanelHost.hud(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        boolean statusBars = mc.gameMode != null && mc.gameMode.hasExperience();
        return new PanelHost.Frame(sw, sh, RegionMath.statusColumn(sw, sh, statusBars), null);
    });

    /** Both, bottom to top. */
    public static List<PanelHost> all() {
        return List.of(WINDOW, STATUS);
    }

    /**
     * The side vanilla draws the offhand slot on beside the hotbar this frame, or
     * {@code null} when it draws none. Mirrors {@code Hud.extractItemHotbar} (26.x):
     * the item hotbar is drawn for the camera's player outside spectator mode (a
     * spectator gets the spectator bar instead), and the offhand slot only while
     * that player's offhand holds something, on the side opposite the main arm
     * (left for a right-handed player, right for a left-handed one). The geometry
     * this feeds lives in {@code RegionMath.resolveHud}; this only reads the game.
     */
    private static @Nullable HumanoidArm drawnOffhandSide(Minecraft mc) {
        if (mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return null;
        if (!(mc.getCameraEntity() instanceof Player player)) return null;
        if (player.getOffhandItem().isEmpty()) return null;
        return player.getMainArm().getOpposite();
    }
}
