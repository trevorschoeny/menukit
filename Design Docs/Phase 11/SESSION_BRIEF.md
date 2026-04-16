# Phase 11 — Session Brief

Whole-phase handoff anchor. Read this first when picking up Phase 12 planning; cross-reference `POST_PHASE_11.md`, per-consumer `REPORT.md` files, and `COMMON_FRICTIONS.md` as needed.

---

## Arc summary

Phase 11 was scoped as *"rebuild four consumer mods against current MenuKit."* Mid-phase it became clear the work had a second, more structural purpose: **generating Phase 12 requirements through pressure-testing the library.** Consumer-mod rebuilds surfaced primitive gaps in MenuKit — gaps that weren't visible from library design alone. Each primitive gap became a mechanism candidate filed for Phase 12 design, with real multi-consumer evidence attached.

The arc reframed (2026-04-15) from *"rebuild + possibly extend library"* to **three-phase:**

- **Phase 11** — rebuild consumers as far as current MenuKit allows; defer features that need primitives that don't exist; file the primitives. **COMPLETE.**
- **Phase 12** — design and ship MenuKit primitives using Phase 11's filed evidence.
- **Phase 13** — complete deferred consumer-mod features against Phase 12's primitives.

The reframing was not a planning failure. It was the work doing what it needed to do. Worth naming as a pattern for future efforts: **consumer-mod rebuild phases tend to surface library requirements.** Scoping them as *"rebuild + file"* from the start (rather than *"rebuild fully"*) is more honest to how the work actually flows. Phase 11's reframing costs were low because the discipline (defer + file) was adopted early enough that few features got hand-rolled before the pattern settled — but see the F15 process finding below for the one case where the discipline was violated mid-phase.

---

## Consumer mods rebuilt

Per-mod close-out reports at `Design Docs/Phase 11/<mod>/REPORT.md`:

- **inventory-plus** — Layers 0a → 5 all shipped. Eleven features deferred (F1–F15, some subsumed); five mechanism candidates filed (M1, M3, M4, M5, M6; M2 shipped mid-phase as the sole library exception). Cross-mod public API at `inventory-plus/PUBLIC_API.md`. See `Design Docs/Phase 11/inventory-plus/REPORT.md`.
- **shulker-palette** — toggle button rebuilt; peek palette toggle (SP-F1) deferred pending IP's F15. Docs at `Design Docs/Phase 11/shulker-palette/`.
- **sandboxes** — inventory buttons rebuilt. Docs at `Design Docs/Phase 11/final-consumers/`.
- **agreeable-allays** — zero code changes needed; all MenuKit imports live. Docs at `Design Docs/Phase 11/final-consumers/`.

**Full monorepo** (menukit + 4 consumers + offrail) loads clean together in the dev client. All five MenuKit canonical contracts pass `/mkverify all`.

---

## Deferral inventory

Full entries in `POST_PHASE_11.md`. Summary:

- **IP**: F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F15 (11 entries; F11–F14 subsumed into F15 when peek UI reverted to stub)
- **shulker-palette**: SP-F1 (peek toggle — sequencing-blocked on IP F15, not primitive-blocked)

Each entry names what Phase 13 delivers, the blocker (primitive or sequencing), and a rough implementation sketch.

---

## Mechanism candidates for Phase 12

Four independent categories. Phase 12 designs each on its own; they don't share implementation.

| Entry | Category | Enables | Evidence source |
|-------|----------|---------|-----------------|
| M1 | persistence-shaped — per-slot state across sessions | F1, F2, future per-slot state needs | IP sort-lock only; single-consumer evidence |
| M3 | cleanup — MKFamily disposition | Phase 13 config refactors IF removed | IP + all four consumer mods; decision-only, not design |
| M4 | integration-shaped — vanilla-menu slot injection | F8, F9 | IP equipment + pockets panels; two IP features |
| M5 | layout-shaped — context-scoped region positioning | Collision-free multi-consumer layout | Sandboxes ↔ IP settings-gear collision + shulker-palette toggle at fixed coords |
| M6 | rendering-shaped — client-side slot primitive for decoration panels | F15 | IP peek (hand-rolled attempt reverted); single-consumer but architecturally unambiguous |

**M2 (SlotIdentity)** shipped mid-Phase 11 as the sole exception to "no library additions during Phase 11." The exception clause is closed; all other primitive needs defer.

### Suggested Phase 12 sequence

If tackling serially, suggest: **M6 → M4 → M1 → M5**.

- **M6 first** — single unambiguous use case (peek panel UI), smallest scope, unlocks the Process Finding's lesson by actually shipping the primitive that was supposed to exist. Good warm-up for Phase 12 primitive-design mechanics.
- **M4 second** — unblocks the most user-visible deferrals (F8 equipment panel + F9 pockets panels, the entire attachment-dependent UI surface). Architecturally constrained (vanilla's slot sync protocol treats `menu.slots` as immutable after construction) so the design space is narrow — that's helpful after M6's design establishes patterns.
- **M1 third** — single-consumer evidence (IP only) but clear use case. Can ship any time; no interlock with M4 or M6.
- **M5 fourth** — **awaits Trevor's region-set decision**. Do not begin implementation until the exact region set per context is committed. Low-urgency — coordinate collisions are currently worked around with manual offsets, not a blocker.

M3 (MKFamily disposition) is a decision, not a design. Resolve any time; orthogonal to M1/M4/M5/M6.

---

## Phase 13 feature dependency graph

Full graph in `inventory-plus/REPORT.md`. Summary:

**M1 ships →** F1 (lock persistence), F2 (chest-slot lock visibility). Ship together; same backing store.

**M4 ships →** F8 (equipment panel), F9 (pockets panels). Ship together; same primitive pattern.

**M6 ships →** F15 (peek panel UI) → **transitively** SP-F1 (shulker-palette peek toggle). F15 in IP's Phase 13; SP-F1 in shulker-palette's Phase 13.

**Already-unblocked (no primitive dependency; schedule-permitting):** F3 (full-lock Ctrl+click), F4 (sort consolidation), F5 (creative sort) + F6 (creative bulk-move) together, F7 (hotbar↔main bulk-move), F10 (speculative pocket HUD toggle).

---

## Architectural invariants for Phase 12 + Phase 13

Preserve these decisions from Phase 11's layer work:

- **Click-sequence architecture for mutations through vanilla menus.** Sort, bulk-move, move-matching all use `ClickSequenceC2SPayload`; server replays via `menu.clicked(...)`. Future click-model features (F3 full-lock, F4 sort consolidation) should extend this pattern rather than reimplement mutation paths.
- **Canonical-slot routing via `SlotIdentity`.** Creative's two-menu split (client `ItemPickerMenu` vs server `InventoryMenu`) is solved by routing through `player.containerMenu` + `IPRegionGroups.canonicalSlot`. For creative-specific UI (Survival Inventory tab), route through `player.inventoryMenu` because server's containerMenu == inventoryMenu in creative.
- **Sort-lock is auto-routing-scoped.** Blocks `moveItemStackTo` + `QUICK_MOVE`, not direct `PICKUP`. F3 (full-lock) is a separate primitive with broader semantics; don't merge them.
- **Three-mixin inventory-screen injection surface.** `InventoryContainerMixin` (primary on `AbstractContainerScreen`) + `RecipeBookMixin` (supplementary on `AbstractRecipeBookScreen` gated `instanceof InventoryScreen`, fixes Phase 10 failure mode #1) + `CreativeInventoryMixin` (supplementary on `CreativeModeInventoryScreen`, fixes Phase 10 failure mode #2). Each feature adds hooks to the same three classes.
- **Attachment accessor contract.** `IPPlayerAttachments.getOrInit*` (not `get*`) in any caller that fires before inventory interaction. Hard contract per COMMON_FRICTIONS #1.
- **Peek server handlers branch on `sourceType` directly.** No `PeekItemSource` abstraction — inline storage access against vanilla components. New peekable types add a case to `ContainerPeek.readContents` / `writeContents` / `detectSourceType`.
- **Library-not-platform.** MenuKit provides primitives; consumers compose them. No defaults, ambient behavior, cross-consumer mediation, or implicit registration. Phase 12 primitive design respects this.

---

## Key lessons

### F15 process finding — architectural discipline first

A Layer 3 pass shipped a hand-rolled `PeekDecoration` with raw `graphics.fill()` calls + custom hit-testing. This violated the conforming-to-primitives principle — peek slots should use vanilla-style slot rendering + slot-click semantics, but that requires a primitive (M6) that didn't exist. Reverted to a stub on advisor review.

**Lesson:** when a primitive gap surfaces mid-feature, stop and file the mechanism. Don't hand-roll a workaround. Workarounds:

- violate the conforming-to-primitives principle (the whole point of the framing)
- ship visually non-conforming code (users notice)
- need to be thrown away when the primitive lands anyway
- obscure the real gap (makes Phase 12 think the primitive is less urgent)

**Signal for future efforts:** if you reach for raw `graphics.fill()` where a primitive should exist, build custom hit-testing where `Button`/`Slot` semantics should apply, or hand-roll panel backgrounds where `PanelStyle` should work — that's the signal to stop and file the mechanism. Workaround slower than deferral because the deferral is a real deliverable; the workaround is disposable.

Captured as global instruction in `~/.claude/CLAUDE.md` ("Architectural Discipline First, Functionality Second") and in per-project memory.

### Conforming-to-primitives discipline

The default answer when implementation needs something the library doesn't provide: **defer + file, don't work around.** Exceptions require meeting a strict test — purely additive, demonstrably needed, tightly scoped, impossible to reasonably defer. If any clause fails, defer.

Applied to Phase 11: the exception clause authorized `SlotIdentity` once (M2), then closed. Everything else that surfaced as a primitive need was deferred and filed (M1, M4, M5, M6).

### Exception clause mechanics

The exception clause format — *"one approved library addition, then closed for new business"* — worked because:
- It gave advance permission for the most obvious gap (SlotIdentity was clearly needed mid-Phase 1)
- It discouraged drift by being single-use and explicit
- Once exercised, the discipline reverted to strict defer-and-file for everything else

Future phases with similar shape (consumer-mod rebuild pressure-testing a library) can adopt this format: advance-permission for one or two known-inevitable gaps, then closed.

### Evidence-driven mechanism design

Each M* entry carries implementation pressure as evidence. M4's evidence is IP's F8 + F9 with specific shape hints. M6's evidence is IP's F15 with a concrete failed hand-rolled attempt. M5's evidence is multi-consumer (sandboxes manual coordinate offset + shulker-palette fixed coords).

**Pattern:** don't file speculative mechanisms. File primitives that concrete features need, with notes on what shape the primitive took when hand-rolled (if applicable) and what specifically broke. Phase 12 design starts from real pressure, not imagination.

### Phase 11's structural shift

Phase 11 was planned as "rebuild four consumer mods" and ended up being "generate Phase 12 requirements through pressure-testing the library." The shift happened because:

- MenuKit's library surface (Phases 6–10) was designed with best-guesses about what consumers need
- Actual consumer-mod rebuild pressure revealed gaps the guesses missed
- The deferral discipline captured each gap as a mechanism candidate with real evidence

Pattern worth naming: **consumer-mod rebuild phases tend to surface library requirements.** Future efforts should scope them as *"rebuild + file"* from the start rather than *"rebuild fully"* — the latter is dishonest about how the work actually flows.

---

## Where we are now (checkpoint)

- **Phase 11:** COMPLETE. Four consumer mods rebuilt, four mechanism candidates filed with evidence, eleven frictions captured, architectural discipline precedent established.
- **Next concrete action:** wait for Trevor to kick off Phase 12. No urgency. Phase 12 is new planning territory; the suggested M6 → M4 → M1 → M5 sequence is a starting point for that conversation, not a commitment.
- **Working tree:** clean (all Phase 11 work committed in a 4-commit sequence; see recent commits on main).

### Half-formed thoughts worth surfacing

- **Phase 12 scope shape.** Four mechanism candidates is a lot to do serially but probably less than a full phase each. Maybe Phase 12 is itself three sub-phases (12a/b/c), or maybe it's one phase with parallel mechanism design tracks. The answer depends on how intertwined M1/M4/M5/M6 turn out to be during design — probably less than feared (they're independent categories by construction) but Phase 12 planning should think about it.

- **M5's region-set decision.** Trevor said "don't begin implementation until I commit the region sets." That's right, but also worth surfacing: M5's design maturity is lower than M1/M4/M6 because of this. If Phase 12 tackles M5 early, it might stall waiting for the decision. Tackling M6 → M4 → M1 → M5 surfaces M5's blocker last, when there's plenty of runway to make the decision.

- **Shulker-palette SP-F1 as Phase 12/13 coordination test.** SP-F1 is sequencing-blocked on IP's F15 (not primitive-blocked). When IP F15 ships in Phase 13, shulker-palette's Phase 13 work picks up SP-F1 as a cross-mod integration test of the public API surface shipped in IP Layer 4. Worth naming as the "does cross-mod integration actually work" validation point.

- **POST_PHASE_11.md file growth.** Currently at 12 features + 6 mechanisms. Phase 12 design will likely produce design docs per mechanism (ARCHITECTURE_M1.md, etc.) rather than expanding POST_PHASE_11.md. The file's role is "filed during Phase 11" — Phase 12 reads it but doesn't edit it.

### Handoff protocol

A fresh agent picking up Phase 12 should:

1. Read this file first.
2. Read the four M* entries in `POST_PHASE_11.md` thoroughly.
3. Read IP's REPORT.md (the primary-evidence mod's close-out).
4. Ask Trevor which mechanism Phase 12 opens with. Default suggestion: M6. But this is planning territory, not execution — expect a planning session, not implementation.

Don't start implementation without a Phase 12 plan document. Phase 11 taught that consumer-mod rebuild pressure reshapes plans mid-work; Phase 12 primitive design will have analogous pressures that deserve explicit upfront scoping.
