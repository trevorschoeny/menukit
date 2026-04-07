package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

/**
 * Defines a single track (column or row) in a mixed grid layout.
 * Currently supports pixel sizes only. Extensible to proportional (fr) later.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKGridTrack(int size) {}
