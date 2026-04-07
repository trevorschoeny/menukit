package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Defines a single tab within a tabbed container.
 * Each tab has a label and/or icon, and a content group that is shown
 * when the tab is active.
 *
 * <p>Part of the <b>MenuKit</b> layout system.
 */
public record MKTabDef(
        @Nullable Component label,
        @Nullable Identifier icon,
        int iconSize,
        MKGroupDef contentGroup
) {}
