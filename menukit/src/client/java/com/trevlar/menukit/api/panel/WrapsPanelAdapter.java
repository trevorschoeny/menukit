package com.trevlar.menukit.api.panel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a thin wrapper around {@link ScreenPanelAdapter},
 * {@link VanillaScreenPanelAdapter} or {@link AnchorPanelAdapter}, so {@link
 * com.trevlar.menukit.inject.PanelHost#captureCallerModId()} walks past it to credit
 * the mod that actually called the wrapper, not the library that wrote it.
 *
 * <p>For libraries that wrap an adapter, such as SlotKit's own panel adapter: put
 * this on the wrapper class itself. Every constructor on that class is skipped by
 * the frame walk, so the credited caller is whoever called the wrapper, however many
 * wrapper layers there are.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WrapsPanelAdapter {
}
