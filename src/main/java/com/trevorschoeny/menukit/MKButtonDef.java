package com.trevorschoeny.menukit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Immutable blueprint for an {@link MKButton}. Created at mod init time by
 * {@link MKPanel.Builder#button}, materialized into a live MKButton during
 * screen initialization.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public record MKButtonDef(
        int childX,                                         // panel-relative x
        int childY,                                         // panel-relative y
        int width,                                          // button width
        int height,                                         // button height
        @Nullable Identifier icon,                          // sprite icon
        @Nullable Identifier toggledIcon,                   // icon when toggled on
        int iconSize,                                       // icon render size
        Component label,                                    // text label
        boolean toggleMode,                                 // toggle on/off behavior
        boolean initialPressed,                             // initial toggle state
        @Nullable String groupName,                         // radio group name (resolved at creation)
        @Nullable Consumer<MKButton> onClick,               // click callback
        @Nullable BiConsumer<MKButton, Boolean> onToggle,   // toggle callback
        @Nullable Component tooltip,                        // hover tooltip text
        @Nullable String opensScreenName,                   // opens a standalone MKScreen by panel name
        @Nullable Supplier<Screen> opensScreenFactory,      // opens an arbitrary screen via mc.setScreen
        @Nullable String togglesPanelName,                   // toggles visibility of a panel by name
        MKButton.ButtonStyle buttonStyle,                    // visual style (STANDARD or SLEEK)
        boolean disabled,                                    // starts disabled (grayed out, not clickable)
        @Nullable BooleanSupplier disabledWhen,              // runtime predicate: button hidden when true
        @Nullable BooleanSupplier pressedWhen,               // runtime predicate: button shows pressed when true
        @Nullable Supplier<Component> tooltipSupplier        // dynamic tooltip (overrides static tooltip each frame)
) {

    /**
     * Creates a live {@link MKButton} from this definition, positioned absolutely
     * in screen space based on the parent panel's position.
     *
     * @param panelX  the panel's container-relative x
     * @param panelY  the panel's container-relative y
     * @param padding the panel's inner padding
     * @param leftPos the screen's leftPos offset
     * @param topPos  the screen's topPos offset
     * @param groups  shared group registry for resolving radio groups by name
     * @return a fully configured MKButton ready to be added to the screen
     */
    public MKButton createButton(int panelX, int panelY, int padding,
                                 int leftPos, int topPos,
                                 java.util.Map<String, MKButtonGroup> groups) {
        // Screen-space position: leftPos + panel position + padding + child offset
        // Note: NO +1 here (unlike slots). The +1 is only for slots to account for
        // the slot background extending 1px before the slot position.
        int absX = leftPos + panelX + padding + childX;
        int absY = topPos + panelY + padding + childY;

        MKButton.Builder builder = MKButton.builder()
                .pos(absX, absY);

        // Only set size if explicitly specified (width > 0)
        // Otherwise let MKButton auto-size to fit content
        if (width > 0 && height > 0) {
            builder.size(width, height);
        }

        // Visual style
        builder.buttonStyle(buttonStyle);

        // Content
        if (icon != null) builder.icon(icon).iconSize(iconSize);
        if (toggledIcon != null) builder.toggledIcon(toggledIcon);
        if (label != null && !label.getString().isEmpty()) builder.label(label);

        // Toggle
        if (toggleMode) builder.toggle();
        if (initialPressed) builder.pressed(true);

        // Group
        if (groupName != null) {
            MKButtonGroup group = groups.computeIfAbsent(groupName, k -> new MKButtonGroup());
            builder.group(group);
        }

        // Build the combined onClick handler that fires:
        // 1) opensScreen actions (by name or factory)
        // 2) togglesPanel action
        // 3) user-provided onClick callback
        Consumer<MKButton> combinedOnClick = buildCombinedOnClick();
        if (combinedOnClick != null) builder.onClick(combinedOnClick);

        if (onToggle != null) builder.onToggle(onToggle);

        // Tooltip
        if (tooltip != null) builder.tooltip(tooltip);

        MKButton btn = builder.build();

        // Post-build properties — can't be set via builder
        if (disabled) btn.active = false;
        if (pressedWhen != null) btn.setPressedWhen(pressedWhen);
        if (tooltipSupplier != null) btn.setTooltipWhen(tooltipSupplier);

        return btn;
    }

    /**
     * Creates a button using flow-computed positions instead of this def's
     * childX/childY. Used when the parent panel has a flow layout.
     */
    public MKButton createButtonAt(int panelX, int panelY, int padding,
                                    int leftPos, int topPos,
                                    int flowChildX, int flowChildY,
                                    java.util.Map<String, MKButtonGroup> groups) {
        int absX = leftPos + panelX + padding + flowChildX;
        int absY = topPos + panelY + padding + flowChildY;

        MKButton.Builder builder = MKButton.builder().pos(absX, absY);
        if (width > 0 && height > 0) builder.size(width, height);
        builder.buttonStyle(buttonStyle);
        if (icon != null) builder.icon(icon).iconSize(iconSize);
        if (toggledIcon != null) builder.toggledIcon(toggledIcon);
        if (label != null && !label.getString().isEmpty()) builder.label(label);
        if (toggleMode) builder.toggle();
        if (initialPressed) builder.pressed(true);
        if (groupName != null) {
            MKButtonGroup group = groups.computeIfAbsent(groupName, k -> new MKButtonGroup());
            builder.group(group);
        }
        Consumer<MKButton> combinedOnClick = buildCombinedOnClick();
        if (combinedOnClick != null) builder.onClick(combinedOnClick);
        if (onToggle != null) builder.onToggle(onToggle);
        if (tooltip != null) builder.tooltip(tooltip);
        MKButton btn = builder.build();
        if (disabled) btn.active = false;
        if (pressedWhen != null) btn.setPressedWhen(pressedWhen);
        if (tooltipSupplier != null) btn.setTooltipWhen(tooltipSupplier);
        return btn;
    }

    /**
     * Builds a combined onClick handler that chains opensScreen, togglesPanel,
     * and user onClick together. Returns null if none are set.
     */
    private @Nullable Consumer<MKButton> buildCombinedOnClick() {
        Consumer<MKButton> combined = null;

        // opensScreen by panel name — sends a packet to the server to open a standalone screen
        if (opensScreenName != null) {
            String name = opensScreenName;
            combined = btn -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    MenuKit.openScreen(mc.player, name);
                }
            };
        }

        // opensScreen by factory — opens a client-side screen directly
        if (opensScreenFactory != null) {
            Supplier<Screen> factory = opensScreenFactory;
            Consumer<MKButton> prev = combined;
            combined = btn -> {
                if (prev != null) prev.accept(btn);
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(factory.get());
            };
        }

        // togglesPanel — toggles visibility of a named panel
        if (togglesPanelName != null) {
            String name = togglesPanelName;
            Consumer<MKButton> prev = combined;
            combined = btn -> {
                if (prev != null) prev.accept(btn);
                MenuKit.togglePanel(name);
            };
        }

        // User-provided onClick — fires after the built-in actions
        if (onClick != null) {
            Consumer<MKButton> userOnClick = onClick;
            Consumer<MKButton> prev = combined;
            combined = btn -> {
                if (prev != null) prev.accept(btn);
                userOnClick.accept(btn);
            };
        }

        return combined;
    }
}
