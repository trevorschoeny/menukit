package com.trevorschoeny.menukit;

/**
 * Declares a one-way transfer relationship between two regions: items in
 * {@code sourceRegion} can be transferred to {@code targetRegion}.
 *
 * <p>Registered via {@link RegionGroupBuilder#transferRoute(String, String)}
 * or as a bidirectional pair via {@link RegionGroupBuilder#transferPair(String, String)}.
 *
 * <p>Used by {@link MKTransferTopology} to answer "given this slot, where
 * should items transfer to?"
 *
 * <p>Part of the <b>MenuKit</b> gesture-to-action framework.
 */
public record MKTransferRoute(String sourceRegion, String targetRegion) {}
