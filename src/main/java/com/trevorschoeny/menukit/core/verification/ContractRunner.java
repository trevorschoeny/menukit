package com.trevorschoeny.menukit.core.verification;

import java.util.List;

/**
 * A slot-free abstraction for running a contract suite and producing results.
 *
 * <p>Per §0043 (Complete-on-Side Feature Ownership) + §0044 (Slot Observation
 * Refinement): this interface lives in MenuKit's {@code core/verification/}
 * package as the shared abstraction between MK-side consumer mods (which
 * compose their own contract lists targeting MK-only features — palette
 * smokes, region observation against vanilla menus, layout/opacity probes)
 * and MKC-side consumer mods (which may compose contract lists targeting
 * MKC-only features — owned slot state, slot grafting, storage attachments).
 *
 * <p>The interface is intentionally minimal — just a {@code run()} method
 * returning a list of {@link ContractResult}s. Implementations are free to
 * organize their internal contract suites however they want; the runner
 * surface is the only thing that crosses the abstraction boundary.
 *
 * <p>Per §0043 (Complete-on-Side): MK consumers use this interface +
 * {@link com.trevorschoeny.menukit.screen.MKContractScreen} as a complete
 * client-side scaffolding. MKC consumers either use the same interface for
 * parallel scaffolding or use their own MKC-side display surfaces — both
 * sides are complete features on their respective sides; neither completes
 * the other via callback-back-edges.
 */
@FunctionalInterface
public interface ContractRunner {

    /**
     * Runs the contract suite and returns the results.
     *
     * <p>Called once when the display surface is opened (e.g., from
     * {@link com.trevorschoeny.menukit.screen.MKContractScreen}'s constructor).
     * Implementations should run all contracts synchronously and return the
     * full result list — no streaming, no async, no incremental updates.
     *
     * @return contract results, in display order
     */
    List<ContractResult> run();
}
