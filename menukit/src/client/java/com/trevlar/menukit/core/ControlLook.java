package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which art a Panel-look control draws under the active resource packs (spec part 10,
 * "every Panel-look element follows its vanilla equivalent"). The Panel look draws
 * MenuKit's own art, which no pack touches; the Default look draws vanilla's widget
 * sprites, which nearly every pack restyles. So each control is mapped, once, here, to its
 * vanilla equivalent, and per control (spec part 1's order, as for panels):
 * <ol>
 *   <li>a pack overrides MenuKit's own sprite for it: MenuKit's drawing, with that sprite;</li>
 *   <li>otherwise, a pack restyled any of its vanilla sprites: it draws as in the Default
 *       look, vanilla's sprites and vanilla's text colours for that control (white on a
 *       button, light grey disabled), through {@link VanillaLook} and the element's own
 *       Default-look code;</li>
 *   <li>otherwise MenuKit's art, so an unmodded game is unchanged to the pixel.</li>
 * </ol>
 * Any one sprite of a control's set being a pack's makes the whole set vanilla's: what the
 * pack left alone is vanilla's own, which is what vanilla's screens show under that pack
 * too, and a control never mixes two looks across its states.
 *
 * <h3>The mapping</h3>
 * <ul>
 *   <li><b>Button, Toggle, a dropdown's trigger and its open list, ColorField, the item
 *       dialog's row controls</b>: MenuKit's {@code menukit:control_raised};
 *       {@link Control#BUTTON}, vanilla's {@code widget/button}, {@code _highlighted},
 *       {@code _disabled} (an open list is {@code _disabled}, as in the Default look). A
 *       Toggle that is on is the button pressed in, MenuKit's pressed look on vanilla's
 *       button ({@link VanillaLook#pressedOverlay}), since vanilla's button has no on state
 *       and a Panel-look toggle's label need not say its state.</li>
 *   <li><b>Checkbox's box</b>: drawn with fills; {@link Control#CHECKBOX}, vanilla's
 *       {@code widget/checkbox}, {@code _highlighted}, {@code _selected},
 *       {@code _selected_highlighted}.</li>
 *   <li><b>Slider, text field, Tabs</b>: already vanilla's widgets and sprites
 *       ({@code widget/slider*}, {@code widget/text_field*}, {@code widget/tab*}) in both
 *       looks, so they follow a pack as they are.</li>
 *   <li><b>Scrollbar</b>: its handle is already vanilla's
 *       {@code container/creative_inventory/scroller}; its track has no sprite of vanilla's
 *       (the creative screen draws its track into its tab art) and keeps MenuKit's.</li>
 *   <li><b>A slot on a plain panel; a dialog</b>: panel surfaces, cut from the pack's
 *       chest, else {@code container/slot} ({@link PackLook}).</li>
 *   <li><b>Radio, InfoBox, a Flow card, ProgressBar</b>: no vanilla equivalent; MenuKit's
 *       art, their text reading on the panel under them.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class ControlLook {

    private ControlLook() {}

    /** A Panel-look control with a vanilla equivalent: MenuKit's own sprite for it, if any, and vanilla's sprites. */
    public enum Control {
        BUTTON("control_raised", "widget/button", "widget/button_highlighted", "widget/button_disabled"),
        CHECKBOX(null, "widget/checkbox", "widget/checkbox_highlighted", "widget/checkbox_selected",
                "widget/checkbox_selected_highlighted");

        private final @Nullable Identifier own;
        private final Identifier[] vanilla;

        Control(@Nullable String own, String... vanilla) {
            this.own = own == null ? null : Identifier.fromNamespaceAndPath("menukit", file(own));
            this.vanilla = new Identifier[vanilla.length];
            for (int i = 0; i < vanilla.length; i++) this.vanilla[i] = Identifier.withDefaultNamespace(file(vanilla[i]));
        }

        private static String file(String sprite) {
            return "textures/gui/sprites/" + sprite + ".png";
        }
    }

    // ponytail: one answer per control, dropped whole on a resource reload (PackLook.onReload).
    private static final Map<Control, Boolean> FOLLOWS = new ConcurrentHashMap<>();

    /** A resource reload: every control is decided again from what the new packs supply. */
    static void onReload() {
        FOLLOWS.clear();
    }

    /**
     * The look {@code control} draws in on a screen of kind {@code screen}: the Default look
     * where the screen is one, or where a pack restyled the control's vanilla sprites and not
     * MenuKit's own; else {@code screen}. Elements draw (and colour their text) by what this
     * returns in place of the screen's kind.
     */
    public static ScreenKind kind(ScreenKind screen, Control control) {
        return screen == ScreenKind.PANEL && follows(control) ? ScreenKind.DEFAULT : screen;
    }

    /** Whether {@code control} draws vanilla's sprites in the Panel look now. */
    public static boolean follows(Control control) {
        return FOLLOWS.computeIfAbsent(control, ControlLook::decide);
    }

    /** Spec part 10's order for one control; no game (the headless checks): MenuKit's art. */
    private static boolean decide(Control c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        ResourceManager rm = mc.getResourceManager();
        if (c.own != null && PackLook.fromPack(rm.getResource(c.own), PackLook.MENUKIT_PACK)) return false;
        for (Identifier v : c.vanilla) if (PackLook.fromPack(rm.getResource(v), PackLook.VANILLA_PACK)) return true;
        return false;
    }
}
