package com.trevorschoeny.menukit.core;

/**
 * Named regions for positioning panels around a MenuKit-native screen's
 * main panel. Mirrors the {@link MenuRegion} values — same naming
 * convention, same flow directions — but anchors to the screen's main
 * panel instead of a vanilla menu's container frame.
 *
 * <p><b>Implementation deferred.</b> Phase 13a did not migrate any
 * standalone-screen consumer. This enum ships as reserved API so
 * {@link PanelPosition#inRegion(StandaloneRegion)} compiles; the actual
 * coordinate-resolution solver is added when a concrete consumer surfaces.
 *
 * <p>{@link #CENTER} was added in Phase 14d-1 alongside {@link MenuRegion#CENTER}
 * for the dialog primitive. The MenuRegion side ships with a working resolver;
 * the StandaloneRegion side is reserved pending the broader solver work
 * (see {@code Design Docs/Elements/DIALOGS.md} §4.5 finding — MenuKit-native
 * screen dispatch for dialogs is filed as a follow-on architectural decision).
 *
 * <p>See {@code Design Docs/Phase 12/M5_REGION_SYSTEM.md} §3.6 for the
 * deferral rationale.
 */
public enum StandaloneRegion {
    LEFT_ALIGN_TOP,
    LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP,
    RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT,
    TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT,
    BOTTOM_ALIGN_RIGHT,

    /**
     * Centered placement (Phase 14d-1 addition for the dialog primitive).
     * Reserved enum value pending the StandaloneRegion resolver work; semantics
     * intended to mirror {@link MenuRegion#CENTER} (centered within the host
     * screen's anchor bounds).
     */
    CENTER
}
