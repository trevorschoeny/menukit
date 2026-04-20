package com.trevorschoeny.menukit.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.menukit.input.MKKeybind;
import com.trevorschoeny.menukit.input.MKKeybindCapture;
import com.trevorschoeny.menukit.input.MKKeybindExt;
import com.trevorschoeny.menukit.input.MKKeybindSync;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Replaces vanilla's single-key capture in {@link KeyBindsScreen} with multi-key
 * High Water Mark capture for ALL keybindings. Uses the {@link MKKeybindExt}
 * duck interface (mixed into every {@link KeyMapping}) for combo access.
 *
 * <p>Capture logic is delegated to {@link MKKeybindCapture}. The mixin's role is to:
 * <ul>
 *   <li>Intercept key/mouse events and route them to the capture engine</li>
 *   <li>Wire up callbacks that apply the result to the selected KeyMapping</li>
 *   <li>Handle GLFW fallback polling in the render loop</li>
 * </ul>
 *
 * <p><b>Release detection:</b> Concrete {@code keyReleased} and {@code mouseReleased}
 * methods are added directly to the mixin. Since {@link KeyBindsScreen} does NOT
 * declare these methods, the JVM resolves the concrete mixin methods over the
 * inherited interface defaults from {@code Screen}'s hierarchy.
 *
 * <p>The {@code mouseReleased} method replicates the default drag cleanup
 * behavior when NOT capturing, since adding a concrete method shadows the
 * interface default.
 *
 * <p>Extends {@link Screen} (rather than implementing {@code ContainerEventHandler})
 * so that {@code super.removed()} resolves to {@code OptionsSubScreen.removed()}
 * for the config sync hook. All {@code ContainerEventHandler} methods
 * ({@code setDragging}, {@code getFocused}) are inherited from {@code Screen}.
 *
 * <p>Runs on CLIENT only. Part of the <b>MenuKit</b> keybind system.
 */
@Mixin(KeyBindsScreen.class)
public abstract class MKKeyBindsScreenMixin extends Screen {

    protected MKKeyBindsScreenMixin() { super(Component.empty()); }

    // ── Vanilla Fields ──────────────────────────────────────────────────────

    @Shadow public KeyMapping selectedKey;
    @Shadow public long lastKeySelection;
    @Shadow private KeyBindsList keyBindsList;

    // ── Shared Capture Engine ───────────────────────────────────────────────

    /** The shared capture engine instance, created lazily when capture begins. */
    @Unique
    private MKKeybindCapture menuKit$capture;

    // ── Capture Engine Factory ──────────────────────────────────────────────

    /**
     * Creates a fresh {@link MKKeybindCapture} engine wired to the currently
     * selected {@link KeyMapping}. The callbacks:
     * <ul>
     *   <li><b>onFinalize</b>: applies the new binding via the
     *       {@link MKKeybindExt} duck interface (universal for all KeyMappings),
     *       sets {@code lastKeySelection}, clears {@code selectedKey}, and
     *       refreshes the list</li>
     *   <li><b>onCancel</b>: sets the binding to UNBOUND (matching vanilla's
     *       Escape=unbind behavior), clears {@code selectedKey}</li>
     *   <li><b>onUpdate</b>: refreshes list entries for live preview</li>
     * </ul>
     */
    @Unique
    private MKKeybindCapture menuKit$createCapture() {
        return new MKKeybindCapture(
                // onFinalize: apply the new binding via the duck interface
                bind -> {
                    if (this.selectedKey != null) {
                        // All KeyMappings now support multi-key combos via the
                        // MKKeybindExt duck interface. updateFromKeybind sets both
                        // the combo and the vanilla base key.
                        MKKeybindExt.updateFromKeybind(this.selectedKey, bind);
                    }
                    this.selectedKey = null;
                    // Critical: set lastKeySelection so vanilla's mouseClicked()
                    // ignores the release event from the capture button click.
                    // Without this, the mouseClicked that follows a fast capture
                    // can re-enter capture mode immediately.
                    this.lastKeySelection = Util.getMillis();
                    if (this.keyBindsList != null) {
                        this.keyBindsList.resetMappingAndUpdateButtons();
                    }
                },
                // onCancel: set to UNBOUND (matching vanilla's Escape=unbind behavior)
                () -> {
                    if (this.selectedKey != null) {
                        MKKeybindExt.updateFromKeybind(this.selectedKey, MKKeybind.UNBOUND);
                    }
                    this.selectedKey = null;
                    this.lastKeySelection = Util.getMillis();
                    if (this.keyBindsList != null) {
                        this.keyBindsList.resetMappingAndUpdateButtons();
                    }
                },
                // onUpdate: refresh list entries for live preview
                () -> {
                    if (this.keyBindsList != null) {
                        this.keyBindsList.refreshEntries();
                    }
                }
        );
    }

    // ── Key Press ───────────────────────────────────────────────────────────

    /**
     * Intercepts keyPressed to route to the shared capture engine. Creates
     * and starts the engine on first key press if needed.
     */
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$onKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (this.selectedKey == null) return;

        // Create capture engine on first key press if needed
        if (menuKit$capture == null || !menuKit$capture.isCapturing()) {
            menuKit$capture = menuKit$createCapture();
            menuKit$capture.start();
            // Register static tracking so the mixin's getTranslatedKeyMessage
            // shows live preview for the mapping being captured
            MKKeybindCapture.activeMapping = this.selectedKey;
            MKKeybindCapture.activeCapture = menuKit$capture;
        }

        InputConstants.Key key = InputConstants.getKey(keyEvent);
        menuKit$capture.onKeyPressed(key);

        // Refresh the list to show live preview of the high water mark
        if (this.keyBindsList != null) {
            this.keyBindsList.refreshEntries();
        }

        // Consume -- don't let vanilla's single-key capture run
        cir.setReturnValue(true);
    }

    // ── Key Release ─────────────────────────────────────────────────────────

    /**
     * Concrete keyReleased method added to KeyBindsScreen. Since the screen
     * class does NOT declare this method, the JVM resolves this concrete mixin
     * method over the inherited default from Screen's hierarchy.
     *
     * <p>Routes release events to the shared capture engine.
     */
    public boolean keyReleased(KeyEvent keyEvent) {
        if (menuKit$capture == null || !menuKit$capture.isCapturing()) {
            // Not capturing -- let normal behavior proceed
            return false;
        }

        InputConstants.Key key = InputConstants.getKey(keyEvent);
        menuKit$capture.onKeyReleased(key);

        return true;
    }

    // ── Mouse Click During Capture ──────────────────────────────────────────

    /**
     * During capture, mouse clicks add mouse buttons to the combo via the
     * shared capture engine.
     */
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$onMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (this.selectedKey == null) return;

        // Create capture engine on first mouse click if needed
        if (menuKit$capture == null || !menuKit$capture.isCapturing()) {
            menuKit$capture = menuKit$createCapture();
            menuKit$capture.start();
            MKKeybindCapture.activeMapping = this.selectedKey;
            MKKeybindCapture.activeCapture = menuKit$capture;
        }

        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
        menuKit$capture.onMousePressed(key);

        if (this.keyBindsList != null) {
            this.keyBindsList.refreshEntries();
        }

        cir.setReturnValue(true);
    }

    // ── Mouse Release ───────────────────────────────────────────────────────

    /**
     * Concrete mouseReleased method added to KeyBindsScreen. Routes release
     * events to the shared capture engine when capturing.
     *
     * <p>When NOT capturing, replicates the default drag cleanup behavior:
     * calls {@code setDragging(false)} and delegates to the focused child
     * element. This is necessary because adding a concrete mouseReleased
     * method shadows the inherited default, so we must replicate its
     * behavior for the non-capturing path.
     */
    public boolean mouseReleased(MouseButtonEvent event) {
        if (menuKit$capture != null && menuKit$capture.isCapturing()) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
            menuKit$capture.onMouseReleased(key);
            return true;
        }

        // Not capturing -- replicate default drag cleanup behavior.
        // setDragging(false) then delegate to focused child.
        this.setDragging(false);
        GuiEventListener focused = this.getFocused();
        if (focused != null) {
            return focused.mouseReleased(event);
        }
        return false;
    }

    // ── GLFW Release Polling ───────────────────────────────────────────────

    /**
     * Polls GLFW for key releases every frame while a capture is active. This
     * is the PRIMARY release detection mechanism for vanilla Controls: the
     * concrete {@code keyReleased} override may not reliably receive events
     * due to mixin/interface default resolution, so we poll directly.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void menuKit$pollReleases(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo ci) {
        if (menuKit$capture != null && menuKit$capture.isCapturing()) {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            menuKit$capture.pollReleases(windowHandle);
        }
    }

    // ── Controls -> Config Sync ─────────────────────────────────────────────

    /**
     * When the Controls screen is removed, sync all KeyMapping combos back to
     * their respective mod configs via {@link MKKeybindSync}. This ensures
     * keybind changes made in the vanilla Controls screen are persisted to
     * disk. Without this, changes are lost on restart.
     *
     * <p>Added as a concrete method rather than {@code @Inject} because
     * {@code KeyBindsScreen} does not override {@code removed()} — only its
     * superclass {@code OptionsSubScreen} does, and Mixin cannot inject into
     * a method that the target class doesn't declare. The concrete override
     * calls {@code super.removed()} (which saves vanilla options) and then
     * syncs keybind configs.
     */
    @Override
    public void removed() {
        super.removed(); // OptionsSubScreen.removed() — saves vanilla options
        MKKeybindSync.syncToConfig();
    }

}
