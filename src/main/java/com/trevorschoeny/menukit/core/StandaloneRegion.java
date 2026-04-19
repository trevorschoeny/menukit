package com.trevorschoeny.menukit.core;

/**
 * Named regions for positioning panels around a MenuKit-native screen's
 * main panel. Mirrors the eight {@link MenuRegion} values — same
 * naming convention, same flow directions — but anchors to the screen's
 * main panel instead of a vanilla menu's container frame.
 *
 * <p><b>Implementation deferred.</b> Phase 13a does not migrate any
 * standalone-screen consumer. This enum ships as reserved API so
 * {@link PanelPosition#inRegion(StandaloneRegion)} compiles; the actual
 * coordinate-resolution solver is added when a concrete consumer surfaces.
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
    BOTTOM_ALIGN_RIGHT
}
