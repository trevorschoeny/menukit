# Phase 11 — Common Frictions

Accumulating findings across consumer-mod refactors. Each entry names a friction that surfaced during implementation and the pattern the consumer used to work around it. Downstream mods (shulker-palette, sandboxes, agreeable-allays — and any future refactors) can skim this file during their audit/plan phase rather than rediscovering each issue.

Organized by subsystem. Update as new frictions surface.

---

## Fabric attachments (fabric-data-attachment-api-v1)

Frictions surfaced during IP Layer 0 (2026-04-15). All consumer mods that register per-player persistent data via Fabric attachments will hit these.

### 1. `AttachmentRegistry.Builder.initializer(...)` doesn't auto-populate on plain `getAttached()`

**Symptom.** Register an attachment with `.initializer(MyData::new)`. First call to `player.getAttached(TYPE)` returns `null` despite the initializer being configured.

**Cause.** Fabric's `initializer(...)` fires in specific paths (sync to client, some copy-on-spawn scenarios) but not on every `getAttached`. "Lazy init on read" is not what it does.

**Consumer pattern.** Provide a `getOrInit*` wrapper on the consumer's attachment facade that does the null-check and explicit `setAttached(new MyData())` fallback. Then make it a hard contract: every caller that needs non-null uses `getOrInit*`, never plain `get*`.

```java
public static MyData getOrInit(Player p) {
    MyData data = p.getAttached(MY_TYPE);
    if (data == null) {
        data = new MyData();
        p.setAttached(MY_TYPE, data);
    }
    return data;
}
```

**Reference:** IP's `IPPlayerAttachments.getOrInitEquipment/getOrInitPockets/getOrInitPocketDisabled` + class-level javadoc stating the contract.

**Alternative considered but not adopted.** Eager initialization via `ServerEntityEvents.ENTITY_LOAD` or `ServerPlayConnectionEvents.JOIN` to pre-populate on player join. More plumbing; call-site pattern is fine.

### 2. `AttachmentSyncPredicate.targetOnly()` emits javac deprecation note

**Symptom.** `./gradlew :yourmod:compileJava` emits `uses or overrides a deprecated API`; noted on the line where `.syncWith(codec, AttachmentSyncPredicate.targetOnly())` is called.

**Cause.** Fabric renamed (or is in the process of renaming) the sync-predicate API surface. Specific replacement TBD — `targetOnly()` still works.

**Consumer pattern.** Ignore the deprecation note for now. Layer 0 verified target-only sync works correctly (write → sync to owning player's client). When the replacement lands, do a sweep across all attachment registrations.

**Reference:** IP's `IPPlayerAttachments` registration block.

---

## 1.21.11 vanilla API changes

Frictions from vanilla-API renames or removals between earlier versions and 1.21.11.

### 3. `CommandSourceStack.hasPermission(int)` removed

**Symptom.** `/gradlew compileJava` error: `cannot find symbol: method hasPermission(int)` on `CommandSourceStack`.

**Cause.** Method was removed or renamed in 1.21.11 mappings.

**Consumer pattern.** If the command is dev-only (registered conditionally via `FabricLoader.isDevelopmentEnvironment()`), drop the permission check — environment gating is sufficient. If the command should be op-only in production, find the replacement API (not yet investigated).

**Reference:** MenuKit's `/mkverify` in `ContractVerification.java` uses no permission gate; IP's `/ip_attach_probe` follows suit.

---

## When to update this file

Each consumer-mod Layer 0/1/2 implementation may surface new frictions. Add a section when:

- A Fabric or vanilla API behaves differently than expected (the "expected" shape being what an experienced 1.21.x Fabric modder would reach for).
- The deviation is likely to affect other consumer mods attempting similar work (not a one-off quirk).
- The consumer workaround is a pattern worth documenting, not just a code-level fix.

One-off bugs, mod-specific workarounds, or findings that only apply to IP's particular combination of features belong in the IP `REPORT.md`, not here.
