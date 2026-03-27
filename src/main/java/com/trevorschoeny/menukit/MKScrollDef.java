package com.trevorschoeny.menukit;

/**
 * Defines a scrollable container with a fixed viewport and scrollable content.
 * The content group can be taller/wider than the viewport -- the scroll offset
 * controls which portion is visible.
 *
 * <p>Scroll offset is stored in {@link MKPanelState} keyed by the element's ID.
 * Rendering (scissor clipping, scrollbar indicator) and input handling
 * (scroll events, interaction clipping) are handled in a follow-up pass --
 * this record is purely structural.
 *
 * <p>Part of the <b>MenuKit</b> scroll container system.
 */
public record MKScrollDef(
        int viewportWidth,      // visible area width in pixels
        int viewportHeight,     // visible area height in pixels
        MKGroupDef contentGroup, // the scrollable content (can be larger than viewport)
        boolean verticalScroll,  // enable vertical scrolling (default true)
        boolean horizontalScroll, // enable horizontal scrolling (default false)
        int scrollSpeed,         // pixels per scroll notch (default 18)
        boolean smoothScroll,    // smooth interpolation (default false)
        boolean showScrollbar    // render scrollbar indicator (default true)
) {}
