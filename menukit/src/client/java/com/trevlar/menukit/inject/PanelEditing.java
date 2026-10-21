package com.trevlar.menukit.inject;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.MK;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Edit mode: the player moving panels (7.4.0). One key, "Move panels" (unbound by default, in
 * MenuKit's category of the Controls screen): on a screen it toggles edit mode there; in game,
 * with no screen open, it opens a clear screen over the HUD where HUD panels can be moved.
 *
 * <p>While editing, every movable placed panel ({@link PanelHost#movable}) has an outline and a
 * hint line sits at the top of the screen. Nothing else takes a click: every mouse button is
 * eaten at the {@code MouseHandler}, before any screen, widget, slot or other mod sees it
 * ({@code MKModalMouseHandlerMixin}). A left-drag moves the panel under the cursor; dropping it
 * stores its place for both modes, or with Shift held for the current mode only
 * ({@link PanelPositions}). A right-click resets it in every mode. Esc or the key leaves; Esc
 * doesn't also close the screen. Every other key reaches the screen as usual.
 *
 * <p>Edit mode belongs to one screen: when that screen closes, or another opens, it is over,
 * and a drag under way is dropped where it started, unsaved.
 */
@ApiStatus.Internal
public final class PanelEditing {

    private PanelEditing() {}

    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final int OUTLINE = 0xFFFFD24A;      // amber: movable
    private static final int OUTLINE_LIT = 0xFFFFFFFF;  // white: under the cursor or being dragged

    private static @Nullable KeyMapping key;

    /** The screen in edit mode, or {@code null}. Kept by identity, so a new screen is never in it. */
    private static @Nullable Screen editing;

    /** A drag in progress: the panel, where on it the cursor took hold, and where it started. */
    private record Drag(Panel panel, int grabX, int grabY, int startX, int startY) {}

    private static @Nullable Drag drag;

    /** The clear screen over the HUD that HUD panels are moved on: no background, no pause. */
    public static final class HudScreen extends Screen {
        HudScreen() {
            super(Component.translatable("menukit.move_panels.title"));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            // Clear: the HUD under it is what is being edited.
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** Registers the key and the per-screen hooks. Called once from {@code MenuKitClient}. */
    public static void init() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.menukit.move_panels", InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("menukit", "menukit"))));

        // In game the key reaches its mapping (screens get their keys first, below).
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) {
                    HudScreen screen = new HudScreen();
                    mc.gui.setScreen(screen);
                    editing = screen;
                    drag = null;
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            ScreenKeyboardEvents.allowKeyPress(screen).register(PanelEditing::allowKey);
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, t) -> follow(s, mx, my));
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, t) -> drawEditing(s, g, mx, my));
            // Closed under a drag or in edit mode (E, the server closing the container, another
            // screen opening over it): edit mode ends with it, so a screen made again or reused
            // (a parent screen coming back) starts out of it. setScreen is under way: end only.
            ScreenEvents.remove(screen).register(s -> {
                if (s == editing) end();
            });
        });
    }

    /** Whether edit mode is on for the open screen. */
    public static boolean active() {
        Screen s = Minecraft.getInstance().gui.screen();
        return s != null && s == editing;
    }

    // Keys: only Esc (while editing) and the key itself are taken; every other key goes on.

    private static boolean allowKey(Screen s, KeyEvent event) {
        if (s == editing && event.isEscape()) {
            leave();
            return false;
        }
        if (key == null || !key.matches(event) || assigningKey(s) || s.isInputCaptured()) return true;
        toggle(s);
        return false;
    }

    /*
     * Typing is vanilla's own answer, Screen.isInputCaptured: the focus chain down to its
     * innermost widget, which says whether it takes text (an EditBox, a MultiLineEditBox such as
     * the book's page, a sign's lines, creative's search, MenuKit's TextField through its
     * stand-in). Vanilla's own screen keys stand down the same way.
     */

    /** Whether {@code s} is waiting for a key or button to bind: vanilla's Controls list, once a binding was clicked. */
    private static boolean assigningKey(Screen s) {
        return s instanceof KeyBindsScreen k && k.selectedKey != null;
    }

    private static void toggle(Screen s) {
        if (s == editing) {
            leave();
        } else {
            editing = s;
            drag = null;
        }
    }

    /** Leaves edit mode (Esc, the key); on the HUD screen that closes it. */
    private static void leave() {
        Screen s = editing;
        end();
        Minecraft mc = Minecraft.getInstance();
        if (s instanceof HudScreen && mc.gui.screen() == s) mc.gui.setScreen(null);
    }

    /** Edit mode is over: an unfinished drag goes with it, never stored, and the panel is back where it was. */
    private static void end() {
        editing = null;
        drag = null;
        PanelPositions.dragging(null, null);
    }

    // The mouse, from MouseHandler.onButton before any screen sees it

    /**
     * One mouse button event on the open screen at {@code (x, y)} (GUI pixels). In edit mode
     * every one is taken: a left press picks up the panel under it, its release drops it, and a
     * right press resets it. A press of the key, when it is bound to a mouse button, toggles
     * edit mode. {@code action}: 1 press, 0 release.
     *
     * @return whether to eat the event, so nothing else on the screen sees it
     */
    public static boolean onButton(int button, int action, boolean shift, double x, double y) {
        Screen s = Minecraft.getInstance().gui.screen();
        if (s == null) return false;
        if (action == 1 && boundTo(button) && !assigningKey(s)) {
            toggle(s);
            return true;
        }
        if (s != editing) return false;
        if (action == 1) {
            PanelHost.Placed hit = panelAt(s, x, y);
            if (hit != null && button == LEFT) {
                drag = new Drag(hit.panel(), (int) x - hit.x(), (int) y - hit.y(), hit.x(), hit.y());
            } else if (hit != null && button == RIGHT) {
                PanelPositions.reset(hit.panel().id());
            }
        } else if (action == 0 && button == LEFT && drag != null) {
            drop(s, x, y, shift);
        }
        return true;
    }

    private static boolean boundTo(int button) {
        if (key == null) return false;
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
        return bound.getType() == InputConstants.Type.MOUSE && bound.getValue() == button;
    }

    /** Before each frame: the dragged panel follows the cursor, live. */
    private static void follow(Screen s, int mouseX, int mouseY) {
        if (s != editing || drag == null) return;
        PanelPositions.Spot at = spotAt(s, drag, mouseX, mouseY);
        if (at == null) {
            // Its panel went (hidden, or the screen laid out again): the drag is over.
            drag = null;
            PanelPositions.dragging(null, null);
            return;
        }
        PanelPositions.dragging(drag.panel().id(), at);
    }

    /** The drop: stores where it landed, for both modes or with Shift this mode only. A press with no move stores nothing. */
    private static void drop(Screen s, double x, double y, boolean shift) {
        Drag d = drag;
        drag = null;
        PanelPositions.Spot at = spotAt(s, d, x, y);
        PanelPositions.dragging(null, null);
        boolean moved = (int) x - d.grabX() != d.startX() || (int) y - d.grabY() != d.startY();
        if (at == null || !moved) return;
        ModeScope now = PanelHost.currentMode();
        PanelPositions.store(d.panel().id(), shift && now != null ? now : ModeScope.BOTH, at);
    }

    /** Where {@code d}'s panel would sit with the cursor at {@code (x, y)}, or {@code null} when it isn't placed. */
    private static PanelPositions.@Nullable Spot spotAt(Screen s, Drag d, double x, double y) {
        for (PanelHost host : hostsOf(s)) {
            PanelHost.Placed p = host.placedOf(d.panel());
            if (p != null) {
                return PanelPositions.Spot.of((int) x - d.grabX(), (int) y - d.grabY(), p.w(), p.h(), s.width, s.height);
            }
        }
        return null;
    }

    /** The topmost movable panel at the point, or {@code null}. */
    private static PanelHost.@Nullable Placed panelAt(Screen s, double x, double y) {
        for (LayerPlan.Drawn d : LayerPlan.topDown(hostsOf(s))) {
            if (PanelHost.movable(d.placed().entry()) && d.placed().contains(x, y)) return d.placed();
        }
        return null;
    }

    /** The hosts whose panels are edited on {@code s}: the HUD's on the HUD screen, else the screen's own. */
    private static List<PanelHost> hostsOf(Screen s) {
        return s instanceof HudScreen ? MK.hudHosts() : ScreenPanelRegistry.hostsOn(s);
    }

    // Drawing, after the screen and its tooltips: the outlines and the hint line.

    private static void drawEditing(Screen s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (s != editing) return;
        PanelHost.Placed hover = drag == null ? panelAt(s, mouseX, mouseY) : null;
        for (LayerPlan.Drawn d : LayerPlan.topDown(hostsOf(s))) {
            PanelHost.Placed p = d.placed();
            if (!PanelHost.movable(p.entry())) continue;
            boolean lit = p == hover || (drag != null && p.panel() == drag.panel());
            g.outline(p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, lit ? OUTLINE_LIT : OUTLINE);
        }
        Font font = Minecraft.getInstance().font;
        Component hint = Component.translatable("menukit.move_panels.hint");
        int half = font.width(hint) / 2 + 4;
        g.fill(s.width / 2 - half, 0, s.width / 2 + half, font.lineHeight + 3, 0xC0000000);
        g.centeredText(font, hint, s.width / 2, 2, 0xFFFFFFFF);
    }
}
