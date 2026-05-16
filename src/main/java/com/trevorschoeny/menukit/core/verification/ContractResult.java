package com.trevorschoeny.menukit.core.verification;

import org.jetbrains.annotations.ApiStatus;

/**
 * A single contract result — pass/fail with a human-readable name and detail.
 *
 * <p>Per §0043 (Complete-on-Side Feature Ownership) + §0044 (Slot Observation
 * Refinement): this record lives in MenuKit's {@code core/verification/}
 * package as the slot-free shared abstraction for contract results. Both MK
 * consumer mods (running MK-only contracts) and MKC consumer mods (running
 * MKC-side contracts, if they choose to use this abstraction) can produce
 * {@code ContractResult} lists for display.
 *
 * <p>The detail field carries free-form text for failure diagnostics or
 * pass-context (e.g., "M11: ConfirmDialog opened, button click handler
 * fired, dismiss returned to caller"). Empty string is acceptable when the
 * name + pass/fail pair is self-explanatory.
 *
 * @param name    human-readable contract name (e.g., "M11 — ConfirmDialog
 *                close-on-confirm")
 * @param passed  true if the contract passed
 * @param detail  free-form detail string; empty if no detail to report
 */
@ApiStatus.Internal
public record ContractResult(String name, boolean passed, String detail) {

    /** Convenience factory for passing contracts with no detail. */
    public static ContractResult pass(String name) {
        return new ContractResult(name, true, "");
    }

    /** Convenience factory for passing contracts with detail. */
    public static ContractResult pass(String name, String detail) {
        return new ContractResult(name, true, detail);
    }

    /** Convenience factory for failing contracts. */
    public static ContractResult fail(String name, String detail) {
        return new ContractResult(name, false, detail);
    }
}
