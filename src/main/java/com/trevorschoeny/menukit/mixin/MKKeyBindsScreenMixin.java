package com.trevorschoeny.menukit.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.menukit.MKKeybind;
import com.trevorschoeny.menukit.MKKeybindCapture;
import com.trevorschoeny.menukit.MKKeybindController;
import com.trevorschoeny.menukit.MKKeybindExt;
import com.trevorschoeny.menukit.MKKeybindSync;
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
 * <p>Capture logic is delegated to {@link MKKeybindCapture}, the shared engine
 * used by both this mixin and the YACL settings widget. The mixin's role is to:
 * <ul>
 *   <li>Intercept key/mouse events and route them to the capture engine</li>
 *   <li>Wire up callbacks that apply the result to the selected KeyMapping</li>
 *   <li>Handle GLFW fallback polling in the render loop</li>
 *   <li>Manage auto-scroll highlighting from the YACL gear icon</li>
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

    // ── Highlight Fade State ────────────────────────────────────────────────

    /** System.currentTimeMillis() when the auto-scroll highlight started, or 0 if inactive. */
    @Unique
    private long menuKit$highlightStartTime = 0;

    /** The list entry index to highlight after auto-scroll. */
    @Unique
    private int menuKit$highlightTargetIndex = -1;

    /** Duration of the highlight in milliseconds (2s hold + 1s fade = 3s total). */
    @Unique
    private static final long HIGHLIGHT_DURATION_MS = 3000;

    /** Duration of the initial full-opacity hold phase in milliseconds. */
    @Unique
    private static final long HIGHLIGHT_HOLD_MS = 2000;

    /** Starting alpha for the highlight overlay (0x40 = ~25%). */
    @Unique
    private static final int HIGHLIGHT_START_ALPHA = 0x40;

    /** Base color for the highlight (soft gold: R=0xFF, G=0xDD, B=0x00). */
    @Unique
    private static final int HIGHLIGHT_BASE_COLOR = 0xFFDD00;

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

    // ── Auto-Scroll to Target Keybind ─────────────────────────────────────

    /**
     * After the keybind list is built, scroll to the entry that matches
     * {@link MKKeybindController#pendingScrollTarget} (set by the gear icon
     * in the YACL settings screen). Consumes the target so it doesn't fire
     * again on subsequent screen opens.
     */
    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void menuKit$scrollToTarget(CallbackInfo ci) {
        KeyMapping target = MKKeybindController.pendingScrollTarget;
        if (target == null || this.keyBindsList == null) return;

        // Consume the target immediately so re-opening the screen
        // from vanilla doesn't re-scroll
        MKKeybindController.pendingScrollTarget = null;

        // Walk the list entries to find the one matching our target KeyMapping,
        // then scroll to center it in the viewport using public APIs.
        var entries = this.keyBindsList.children();
        for (int i = 0; i < entries.size(); i++) {
            KeyBindsList.Entry entry = entries.get(i);
            if (entry instanceof KeyBindsList.KeyEntry keyEntry) {
                KeyMapping entryKey = ((KeyEntryAccessor) (Object) keyEntry).menuKit$getKey();
                if (entryKey == target) {
                    // Replicate AbstractSelectionList.centerScrollOn() using public methods:
                    // scrollAmount = rowTop(index) + itemHeight/2 - (listY + listHeight/2)
                    int itemHeight = 20; // KeyBindsList.ITEM_HEIGHT
                    double center = this.keyBindsList.getY() + this.keyBindsList.getHeight() / 2.0;
                    double scrollAmount = this.keyBindsList.getRowTop(i) + itemHeight / 2.0 - center;
                    this.keyBindsList.setScrollAmount(scrollAmount);

                    // Start highlight fade effect on the scrolled-to row
                    menuKit$highlightStartTime = System.currentTimeMillis();
                    menuKit$highlightTargetIndex = i;
                    return;
                }
            }
        }
    }

    // ── Highlight Fade Rendering + GLFW Safety Fallback ─────────────────────

    /**
     * Renders a fading gold highlight overlay on the auto-scrolled keybind row,
     * AND runs the GLFW polling safety fallback for multi-key capture.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void menuKit$renderHighlight(GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo ci) {
        // ── Active GLFW release polling ──────────────────────────────────
        // Poll every frame for key releases. This is the PRIMARY release
        // detection mechanism for vanilla Controls (the concrete keyReleased
        // method may not reliably receive events due to mixin/interface
        // default resolution). Immediate, no timeout.
        if (menuKit$capture != null && menuKit$capture.isCapturing()) {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            menuKit$capture.pollReleases(windowHandle);
        }

        // ── Highlight rendering ──────────────────────────────────────────
        if (menuKit$highlightStartTime <= 0 || this.keyBindsList == null) return;

        long elapsed = System.currentTimeMillis() - menuKit$highlightStartTime;

        // Expired -- clear state and stop rendering
        if (elapsed >= HIGHLIGHT_DURATION_MS) {
            menuKit$highlightStartTime = 0;
            menuKit$highlightTargetIndex = -1;
            return;
        }

        // Hold at full opacity for HIGHLIGHT_HOLD_MS, then fade over the remainder
        int alpha;
        if (elapsed < HIGHLIGHT_HOLD_MS) {
            alpha = HIGHLIGHT_START_ALPHA;
        } else {
            float fadeProgress = (float) (elapsed - HIGHLIGHT_HOLD_MS) / (HIGHLIGHT_DURATION_MS - HIGHLIGHT_HOLD_MS);
            alpha = (int) (HIGHLIGHT_START_ALPHA * (1.0f - fadeProgress));
        }
        if (alpha <= 0) return;

        // Compute the row's screen-space bounds from the list's public API.
        // getRowTop(index) gives the top Y in screen coords. Row height is 20px.
        int rowTop = this.keyBindsList.getRowTop(menuKit$highlightTargetIndex);
        int rowBottom = rowTop + 20; // KeyBindsList.ITEM_HEIGHT

        // Only render if the row is visible within the list's viewport
        int listTop = this.keyBindsList.getY();
        int listBottom = listTop + this.keyBindsList.getHeight();
        if (rowBottom <= listTop || rowTop >= listBottom) return;

        // Clamp to list viewport so the highlight doesn't bleed outside
        int drawTop = Math.max(rowTop, listTop);
        int drawBottom = Math.min(rowBottom, listBottom);

        // Use the list's X and width for horizontal bounds
        int rowWidth = this.keyBindsList.getRowWidth();
        int listCenter = this.keyBindsList.getX() + this.keyBindsList.getWidth() / 2;
        int drawLeft = listCenter - rowWidth / 2;
        int drawRight = listCenter + rowWidth / 2;

        // Compose ARGB color: alpha << 24 | base RGB
        int color = (alpha << 24) | HIGHLIGHT_BASE_COLOR;
        graphics.fill(drawLeft, drawTop, drawRight, drawBottom, color);
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
