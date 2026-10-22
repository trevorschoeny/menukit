package com.trevlar.menukit.mixin;

import net.minecraft.client.MouseHandler;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Makes {@code MouseHandler.xpos} and {@code ypos} writable so
 * {@link com.trevlar.menukit.input.CursorContinuity} can keep Minecraft's mouse
 * position in sync with the OS cursor after it warps the cursor back.
 *
 * <p>On some screen transitions (notably the async server-driven open path used
 * for a slot library's custom menus), vanilla resets {@code xpos}/{@code ypos} to the window
 * center before the new screen's {@code init()} runs. {@code CursorContinuity}
 * puts the OS cursor back with a warp, but the handler fields don't follow it
 * straight away; without this they stay centered until the mouse moves. The cursor then looks right while
 * hover detection, and anything else reading {@code mouseHandler.xpos()/ypos()},
 * uses the center. Setting both in the same restore step fixes that.
 */
@ApiStatus.Internal
@Mixin(MouseHandler.class)
public interface MouseHandlerCursorPosAccessor {

    @Mutable
    @Accessor("xpos")
    void mk$setXpos(double xpos);

    @Mutable
    @Accessor("ypos")
    void mk$setYpos(double ypos);
}
