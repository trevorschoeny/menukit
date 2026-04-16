# Common Frictions

Accumulating findings across consumer-mod refactors. Each entry names a friction that surfaced during implementation and the pattern the consumer used to work around it. Downstream mods can skim this file during their audit/plan phase rather than rediscovering each issue.

> **Phase-spanning artifact.** This file accumulates across phases — future consumer-mod rebuild phases or Minecraft-version upgrade phases should **append, not replace**. Keep existing entries; add new ones as they surface. Each new entry should note the phase that surfaced it (and, if relevant, the Minecraft version or Fabric API version) for downstream context. The file lives at `Design Docs/Phase 11/` for historical reasons (Phase 11 surfaced the first 11 entries); if its scope outgrows that location across future phases, move it to a phase-neutral path rather than recreating per-phase copies.

Organized by subsystem. Entries within each subsystem are numbered sequentially (don't renumber when adding; append at the bottom of the relevant section or create a new subsystem section).

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

## Mixin method resolution

Frictions from mixin injection target resolution. Surfaced during shulker-palette Layer 0a (2026-04-15).

### 6. `@Inject` with explicit descriptor cannot target inherited methods on a subclass

**Symptom.** Mixin applicator crash at boot: `@Inject annotation on trevorMod$myHandler could not find any targets matching 'mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z' in net/minecraft/client/gui/screens/inventory/ShulkerBoxScreen`.

**Cause.** `ShulkerBoxScreen` does not override `mouseClicked` — the method lives on `AbstractContainerScreen`. When `@Mixin(ShulkerBoxScreen.class)` specifies `method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"`, the Mixin applicator searches only the target class's own methods, not the inheritance chain. Without an explicit descriptor (just `method = "mouseClicked"`), Mixin can sometimes resolve parent methods — but with a descriptor, it requires an exact match on the target class.

**Consumer pattern.** Target the parent class where the method is actually defined and gate with `instanceof`:

```java
@Mixin(AbstractContainerScreen.class)
public class MyScreenMixin {
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void myMod$click(MouseButtonEvent event, boolean flag,
                              CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ShulkerBoxScreen)) return;
        // ... dispatch
    }
}
```

This fires for all `AbstractContainerScreen` instances but short-circuits immediately for non-target screens. The `instanceof` check is cheap.

**Reference:** Shulker-palette's `ShulkerPaletteScreenMixin` — originally targeted `ShulkerBoxScreen` (crashed), fixed to target `AbstractContainerScreen` with `instanceof ShulkerBoxScreen` gate. IP's `InventoryContainerMixin` uses the same pattern (targets `AbstractContainerScreen`, dispatches per-screen via feature-scoped helpers).

---

## 1.21.11 vanilla API changes (continued)

Additional friction clusters surfaced during IP Layer 2 passive-behavior mixin population (2026-04-15). Each one costs a compile-error round-trip if not known in advance.

### 7. `HumanoidRenderState.wingsItem` doesn't exist — field is `chestEquipment`

**Symptom.** `error: cannot find symbol: variable wingsItem, location: variable renderState of type HumanoidRenderState`.

**Cause.** In 1.21.11, `HumanoidRenderState` doesn't carry a dedicated wings item field. Vanilla's `WingsLayer.submit` reads `renderState.chestEquipment` and renders wings only if it has an `EQUIPPABLE` component with a non-empty `assetId`. There's no separate wings storage — the chest armor slot doubles as the wings slot.

**Consumer pattern.** To make WingsLayer render an elytra from a non-chest source, temporarily swap it into `renderState.chestEquipment` at `submit` HEAD and restore at RETURN:

```java
@Inject(method = "submit(...)", at = @At("HEAD"))
private void myMod$swapElytraIn(..., HumanoidRenderState renderState, ..., CallbackInfo ci) {
    if (isElytra(renderState.chestEquipment)) return;  // vanilla handles it
    ItemStack elytra = /* read from your storage */;
    if (!isElytra(elytra)) return;
    this.originalChest = renderState.chestEquipment;
    renderState.chestEquipment = elytra;
    this.swapped = true;
}

@Inject(method = "submit(...)", at = @At("RETURN"))
private void myMod$swapElytraOut(..., HumanoidRenderState renderState, ..., CallbackInfo ci) {
    if (!this.swapped) return;
    renderState.chestEquipment = this.originalChest;
    this.swapped = false;
}
```

**Reference:** IP's `IPWingsLayerMixin`. Similar swap pattern in `IPFallFlyingMixin` for the server-side equipment-elytra durability tick (swaps `EquipmentSlot.CHEST` on the living entity, not the render state).

### 8. `EnchantmentHelper.hasMending(stack)` doesn't exist — use `modifyDurabilityToRepairFromXp`

**Symptom.** `error: cannot find symbol: method hasMending(ItemStack), location: class EnchantmentHelper`.

**Cause.** In 1.21.11's registry-based enchantment system, there's no named `hasMending` predicate. Enchantment effects are expressed through `EnchantmentEffectComponents` like `REPAIR_WITH_XP`. The helper to apply a mending-style repair is `EnchantmentHelper.modifyDurabilityToRepairFromXp(ServerLevel, ItemStack, int xp)` which returns durability-to-repair (0 if the item has no REPAIR_WITH_XP enchantment).

**Consumer pattern.** Combine the has-enchantment check with the actual repair computation — both come from the same call. Mirror vanilla's XP accounting from `ExperienceOrb.repairPlayerItems`:

```java
int durabilityFromXp = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, remainingXp);
if (durabilityFromXp <= 0) continue; // no mending, or no XP to apply

int actualRepair = Math.min(durabilityFromXp, stack.getDamageValue());
stack.setDamageValue(stack.getDamageValue() - actualRepair);

// Proportional XP accounting
if (actualRepair > 0) {
    remainingXp = remainingXp - actualRepair * remainingXp / durabilityFromXp;
}
```

The `actualRepair * remainingXp / durabilityFromXp` formula consumes XP in proportion to how much of the possible repair actually applied — so over-capacity durability doesn't eat extra XP.

**Reference:** IP's `MendingHelper.applyLeftoverXp`. Vanilla reference: `ExperienceOrb.repairPlayerItems` in 1.21.11 sources.

### 9. `GameRules.RULE_KEEPINVENTORY` renamed to `GameRules.KEEP_INVENTORY`

**Symptom.** `error: cannot find symbol: variable RULE_KEEPINVENTORY, location: class GameRules`.

**Cause.** Constants on `GameRules` switched from `RULE_*` naming to the shorter canonical form. `RULE_KEEPINVENTORY` → `KEEP_INVENTORY`; likely the same for other rules (`RULE_MOBGRIEFING` → `MOB_GRIEFING`, etc. — verify per rule).

**Consumer pattern.** Update the constant name. Trivial fix once you know the new name; surfaces as a compile error.

**Reference:** IP's `IPDeathDropsMixin`.

### 10. `GameRules.getBoolean(rule)` replaced by generic `GameRules.get(rule)`

**Symptom.** `error: cannot find symbol: method getBoolean(GameRule<Boolean>), location: class GameRules`.

**Cause.** 1.21.11's `GameRules` uses a generic `<T> T get(GameRule<T>)` method instead of type-specific `getBoolean`/`getInt` accessors. The `GameRule<Boolean>` type parameter ensures the return is the right type without a separate method per type.

**Consumer pattern.**

```java
// Old: level.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)
// New:
boolean keep = level.getGameRules().get(GameRules.KEEP_INVENTORY);
```

Works identically for `GameRule<Integer>` etc. — just use `get(rule)`.

**Reference:** IP's `IPDeathDropsMixin`.

### 11. `GameRules` moved from `net.minecraft.world.level` to `net.minecraft.world.level.gamerules`

**Symptom.** `error: cannot find symbol: class GameRules, location: package net.minecraft.world.level`.

**Cause.** Package reorganization. The `GameRules` class and its associated types (`GameRule`, `GameRuleCategory`, etc.) moved to a dedicated `gamerules` sub-package.

**Consumer pattern.** Update imports:

```java
// Old: import net.minecraft.world.level.GameRules;
// New:
import net.minecraft.world.level.gamerules.GameRules;
```

**Reference:** IP's `IPDeathDropsMixin`.

---

## When to update this file

Each consumer-mod Layer 0/1/2 implementation may surface new frictions. Add a section when:

- A Fabric or vanilla API behaves differently than expected (the "expected" shape being what an experienced 1.21.x Fabric modder would reach for).
- The deviation is likely to affect other consumer mods attempting similar work (not a one-off quirk).
- The consumer workaround is a pattern worth documenting, not just a code-level fix.

One-off bugs, mod-specific workarounds, or findings that only apply to IP's particular combination of features belong in the IP `REPORT.md`, not here.
