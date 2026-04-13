# MenuKit Migration — Deferred Concerns

Items deferred across phases. Scan this list at phase boundaries.

## Architecturally Significant

- **Text rendering investigation** (Task 2 → Phase 4b first task)
  `graphics.drawString()` doesn't render in `renderBg()` or `renderLabels()`, while `fill()` works in both. Hypothesis: text and geometry go through different buffer sources that flush at different pipeline stages in 1.21.11. Investigate before designing the panel element system — the solution shapes the abstraction.

- **Body panel visibility toggle limitation** (Task 2 → probably forced by Phase 4b scroll/tab)
  If a body panel toggles visibility, `imageWidth`/`imageHeight` change in `renderBg()` but `leftPos`/`topPos` don't update (set once in `init()`). Screen would be offset. Not a problem while only relative panels toggle.

## Must-Verify

- **Drag mode full pipeline** (Task 4 → Phase 4b first real drag mode)
  Infrastructure wired (`mouseClicked` → `mouseDragged` → `mouseReleased`) but never exercised with a real drag. First real drag mode port needs to validate the full click→drag→release pipeline.

- **Hover exit events** (Task 4 → verify opportunistically)
  Enter path confirmed working. Exit path logic is sound but should be empirically verified with debug logging.

## Wait for Real Use Case

- **Server-sync for right-click handlers** (Task 3 → when a storage-mutating right-click is needed)
  Current right-click handlers run client-only. Use `clickMenuButton` C2S mechanism when server authority is needed.

- **Modifier support for key registry** (Task 3 → YAGNI, extend to MKKeybind when needed)

## Phase 5 Cleanup

- **MenuKitTestSetup removal** — convert to proper test framework or remove entirely
- **MKSlotMixin's ultimate fate** — already in Phase 5 plan
- **~20 dead methods in MenuKit.java** — harvest or remove
