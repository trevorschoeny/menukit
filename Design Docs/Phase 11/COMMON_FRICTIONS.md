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

### 4. `keyPressed` / `mouseClicked` signatures use `KeyEvent` / `MouseButtonEvent` records

**Symptom.** Dev-client boot crashes with `InvalidInjectionException`: `Expected (Lnet/minecraft/client/input/KeyEvent;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V but found (IIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V` on a keyPressed mixin. Compile is fine; Mixin applicator rejects the signature at load time.

**Cause.** In 1.21.11, vanilla's `Screen.keyPressed` + `AbstractContainerScreen.keyPressed` + `AbstractRecipeBookScreen.keyPressed` all take `(KeyEvent event)` instead of the old `(int keyCode, int scanCode, int modifiers)`. Same pattern applies to `mouseClicked(MouseButtonEvent event, boolean doubleClick)` (Phase 10 already knew about this one). The mixin injection layer doesn't match by method name alone when the descriptor differs — the Mixin annotation's `method = "keyPressed"` succeeds in finding the name but fails when the parameter-list descriptor doesn't match.

**Consumer pattern.** For `keyPressed` mixins on any `Screen` subclass in 1.21.11:

```java
import net.minecraft.client.input.KeyEvent;

@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
private void yourMod$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
    int keyCode = event.key();  // GLFW key code
    // ... dispatch / match / etc.
}
```

`KeyEvent.key()` returns the GLFW key code (what used to be the first int arg). `MKKeybindExt.matchesEvent(mapping, keyCode, modifiers)` documents that modifiers is unused (GLFW polling replaces it), so passing `0` for the modifier arg is safe.

**Reference:** MenuKit's Phase 10 example mixins (`ExampleKeybindTriggeredPanelMixin` + `ExampleKeybindTriggeredPanelRecipeBookMixin`) already use `KeyEvent`. IP's Layer 1 Group A `InventoryContainerMixin` + `RecipeBookMixin` updated to match after dev-client boot caught the signature mismatch.

**Cost.** Compile-time error gives no warning — this surfaces only at dev-client boot as a hard crash in the Mixin applicator. If you're porting mixin code from pre-1.21.5 or pre-1.21.11 era, audit every `keyPressed` / `mouseClicked` / `mouseReleased` / `mouseDragged` / `mouseScrolled` mixin signature and match against current vanilla. Referenced by Phase 10's `INVENTORY_INJECTION_PATTERN.md` § Pattern 2 example (mouseClicked used the new `MouseButtonEvent` signature).

### 5. `Screen.hasShiftDown()` (and peers) removed

**Symptom.** `cannot find symbol: method hasShiftDown() on class Screen`. Code that polls modifier-key state outside of an event callback (e.g. inside `slotClicked`, tick handlers, sound dispatch) can't reach for the old static anymore.

**Cause.** 1.21.11 moved modifier queries onto `net.minecraft.client.input.InputWithModifiers` as instance defaults (`hasShiftDown()`, `hasControlDown()`, `hasAltDown()`). `KeyEvent` + `MouseButtonEvent` implement it, so inside a keyPressed/mouseClicked hook you call it on the event record. Outside of an event context there is no `InputWithModifiers` to call.

**Consumer pattern.** Poll GLFW directly via `InputConstants.isKeyDown(window, key)`:

```java
import com.mojang.blaze3d.platform.InputConstants;

var window = Minecraft.getInstance().getWindow();
boolean shiftHeld = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                 || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
```

Use this when the caller isn't inside a key/mouse event — inside those events, prefer `event.hasShiftDown()` so the modifier state matches the exact event you're handling.

**Reference:** IP's `decoration/BulkMove.isShiftHeld()` — called from `slotClicked` HEAD, which has no `InputWithModifiers` event to read.

---

## When to update this file

Each consumer-mod Layer 0/1/2 implementation may surface new frictions. Add a section when:

- A Fabric or vanilla API behaves differently than expected (the "expected" shape being what an experienced 1.21.x Fabric modder would reach for).
- The deviation is likely to affect other consumer mods attempting similar work (not a one-off quirk).
- The consumer workaround is a pattern worth documenting, not just a code-level fix.

One-off bugs, mod-specific workarounds, or findings that only apply to IP's particular combination of features belong in the IP `REPORT.md`, not here.
