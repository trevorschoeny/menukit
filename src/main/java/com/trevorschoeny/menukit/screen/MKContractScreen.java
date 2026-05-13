package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.TextLabel;
import com.trevorschoeny.menukit.core.verification.ContractResult;
import com.trevorschoeny.menukit.core.verification.ContractRunner;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone screen for displaying contract results. Takes a
 * {@link ContractRunner}, invokes it once on open, and renders the result
 * list as a vertical pass/fail list.
 *
 * <p>Per §0043 (Complete-on-Side Feature Ownership) + §0044 (Slot Observation
 * Refinement): this is a complete MK-side scaffolding feature for consumer
 * mods running MK-only contracts. Opens via
 * {@link Minecraft#setScreen(net.minecraft.client.gui.screens.Screen)} —
 * no server roundtrip, no menu-type registration, no handler subclass. The
 * MK consumer mod composes its contract list as a {@code ContractRunner}
 * implementation and passes it here for display.
 *
 * <p>MKC-side consumer mods MAY also use this screen if their contract
 * results fit the pure-client display shape; otherwise MKC consumers use
 * their own {@code TestContractScreen} (handler-paired) for slot-bearing
 * contract sweeps. Both shapes are complete on their respective sides per
 * §0043 — neither completes the other via cross-boundary callbacks.
 *
 * <p>Display:
 * <ul>
 *   <li>Title (rendered by vanilla {@code Screen.render}).</li>
 *   <li>Summary line ({@code "Contract Results — N passed / M total"}).</li>
 *   <li>One {@link TextLabel} per result, color-coded green (pass) or red
 *       (fail), with the contract name and optional detail.</li>
 *   <li>"Close" button at the bottom to return to the previous screen.</li>
 * </ul>
 *
 * <p>If the result list is long enough to overflow visible screen space,
 * results fall off the bottom — MVP omits scroll. Use a small enough
 * contract list to fit on screen, or extend this class with a
 * {@link com.trevorschoeny.menukit.core.ScrollContainer} wrap if scrolling
 * becomes necessary.
 */
public class MKContractScreen extends MenuKitScreen {

    /** Height of each result row (matches default font line height + small padding). */
    private static final int ROW_HEIGHT = 11;

    /** Height of the close button. */
    private static final int BUTTON_HEIGHT = 16;

    /** Width of the close button. */
    private static final int BUTTON_WIDTH = 60;

    /**
     * Builds a {@link MKContractScreen} from a {@link ContractRunner}.
     * The runner is invoked synchronously in this constructor — by the time
     * the screen is rendered, results are already computed.
     *
     * @param title  title component shown above the result list
     * @param runner contract runner that produces the result list
     */
    public MKContractScreen(Component title, ContractRunner runner) {
        super(title, buildPanels(runner));
    }

    /**
     * Runs the contract runner and builds the panel list. Static so it can
     * be passed to the {@link MenuKitScreen} super constructor.
     */
    private static List<Panel> buildPanels(ContractRunner runner) {
        List<ContractResult> results = runner.run();

        int passCount = 0;
        for (ContractResult r : results) {
            if (r.passed()) passCount++;
        }

        List<PanelElement> elements = new ArrayList<>();
        int y = 0;

        // Summary line — top of the panel, indicates pass/fail counts.
        Component summary = Component.literal(
                String.format("Contract Results — %d passed / %d total",
                        passCount, results.size()))
                .withStyle(passCount == results.size()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW);
        elements.add(new TextLabel(0, y, summary));
        y += ROW_HEIGHT + 4;

        // Per-result rows — vertically stacked.
        for (ContractResult r : results) {
            elements.add(new TextLabel(0, y, formatResult(r)));
            y += ROW_HEIGHT;
        }

        // Close button — below the results.
        y += 8;
        elements.add(new Button(
                100,
                y,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Close"),
                btn -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) {
                        mc.setScreen(null);
                    }
                }));

        Panel panel = new Panel(
                "mk-contract-results",
                elements,
                /*visible=*/ true,
                PanelStyle.RAISED,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);

        return List.of(panel);
    }

    /**
     * Formats a contract result as a colored line. Pass = green, fail = red.
     * Detail appended in gray if present.
     */
    private static Component formatResult(ContractResult r) {
        ChatFormatting nameColor = r.passed() ? ChatFormatting.GREEN : ChatFormatting.RED;
        String marker = r.passed() ? "[PASS] " : "[FAIL] ";
        var line = Component.literal(marker + r.name()).withStyle(nameColor);
        if (!r.detail().isEmpty()) {
            line.append(Component.literal(" — " + r.detail()).withStyle(ChatFormatting.GRAY));
        }
        return line;
    }
}
