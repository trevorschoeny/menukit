package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.MouseHandler;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Phase 16h — accessor mixin exposing {@code MouseHandler.xpos} / {@code ypos}
 * as writable so {@link com.trevorschoeny.menukit.input.CursorContinuity}
 * can keep Minecraft's internal mouse position in sync with the OS cursor
 * after a {@code glfwSetCursorPos} restore.
 *
 * <h3>Why this is necessary</h3>
 *
 * On certain screen transitions (notably the async S2C-mediated open path
 * used for custom MKC menus), vanilla resets {@code MouseHandler.xpos/ypos}
 * to window-center BEFORE the new screen's {@code init()} runs.
 * {@code CursorContinuity} restores the visible OS cursor via
 * {@code glfwSetCursorPos}, but GLFW does NOT fire its cursor-position
 * callback for programmatic moves — so the {@code MouseHandler} fields
 * stay stuck at the centered value until the user physically moves the
 * mouse.
 *
 * <p>That split state (OS cursor preserved + internal state centered) is
 * what allowed cursor preservation to look correct visually while still
 * confusing hover detection and any rendering path that reads
 * {@code mouseHandler.xpos()/ypos()} directly. The fix is to set both
 * the OS cursor and the internal handler fields in the same restore step.
 *
 * <p>Same module + same convention as {@code SlotPositionAccessor}.
 */
@ApiStatus.Internal
@Mixin(MouseHandler.class)
public interface MouseHandlerCursorPosAccessor {

    @Mutable
    @Accessor("xpos")
    void menuKit$setXpos(double xpos);

    @Mutable
    @Accessor("ypos")
    void menuKit$setYpos(double ypos);
}
