package com.trevorschoeny.menukit;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Frontend (client-side) state for a slot. Contains visual decorations,
 * ghost icons, and display state — the "how it looks" concerns.
 *
 * <p>Separated from backend concerns (item validation, persistence, etc.)
 * as part of the MenuKit v2 separation of concerns.
 *
 * <p>Read by {@code MKScreenMixin} rendering code. Never touches item
 * validation or server-side logic.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKSlotFrontendState {

    // ── Ghost Icon ──────────────────────────────────────────────────────
    // Shown when the slot is empty (e.g., armor outline, elytra silhouette)
    private @Nullable Identifier ghostIcon;

    // ── Disabled ────────────────────────────────────────────────────────
    // When disabled, the slot is hidden and blocks interaction (visual concern).
    private @Nullable BooleanSupplier disabledWhen;

    // ── Lock Visual ─────────────────────────────────────────────────────
    // Ctrl+click lock — visual indicator showing the slot is locked.
    private boolean locked;

    // ── Visual Decorations ──────────────────────────────────────────────
    // ARGB color applied as a translucent fill BEHIND the item.
    private int backgroundTint = 0;

    // Icon texture rendered ON TOP of the slot item.
    private @Nullable Identifier overlayIcon = null;

    // ARGB color drawn as a 1px border around the 16x16 slot area.
    private int borderColor = 0;

    // ── Ghost Icon ──────────────────────────────────────────────────────

    public @Nullable Identifier getGhostIcon() { return ghostIcon; }
    public void setGhostIcon(@Nullable Identifier icon) { this.ghostIcon = icon; }

    // ── Disabled ────────────────────────────────────────────────────────

    public @Nullable BooleanSupplier getDisabledWhen() { return disabledWhen; }
    public void setDisabledWhen(@Nullable BooleanSupplier predicate) { this.disabledWhen = predicate; }

    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    public boolean isActive() {
        return !isDisabled();
    }

    // ── Lock Visual ─────────────────────────────────────────────────────

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void toggleLocked() { this.locked = !this.locked; }

    // ── Visual Decorations ──────────────────────────────────────────────

    public int getBackgroundTint() { return backgroundTint; }
    public void setBackgroundTint(int argb) { this.backgroundTint = argb; }

    public @Nullable Identifier getOverlayIcon() { return overlayIcon; }
    public void setOverlayIcon(@Nullable Identifier icon) { this.overlayIcon = icon; }

    public int getBorderColor() { return borderColor; }
    public void setBorderColor(int argb) { this.borderColor = argb; }

    public boolean hasDecoration() {
        return backgroundTint != 0 || overlayIcon != null || borderColor != 0;
    }
}
