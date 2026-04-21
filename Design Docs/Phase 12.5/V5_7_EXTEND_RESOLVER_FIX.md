# β — `SlotGroupCategories.extend` design note

Library-side fix for the primitive gap surfaced in [V5_7_PRECHECK.md](V5_7_PRECHECK.md): consumers grafting into vanilla menus cannot declare a SlotGroupContext category for their grafted slot group, because the library's vanilla resolvers already hold first-wins registration and there is no extension API.

Scope: one method, one class touched, ~20 lines. Ships as its own commit before V5.7 scaffolding resumes.

---

## 1. API shape

```java
public final class SlotGroupCategories {

    /* existing — unchanged */
    public static <T extends AbstractContainerMenu> void register(
            Class<T> menuClass, SlotGroupResolver resolver) { ... }

    /* new */
    public static <T extends AbstractContainerMenu> void extend(
            Class<T> menuClass, SlotGroupResolver resolver) { ... }

    /* existing — behavior updated to chain primary + extensions */
    public static Map<SlotGroupCategory, List<Slot>> of(AbstractContainerMenu menu) { ... }
}
```

- `register` semantics unchanged: **one primary resolver per class, first-wins on duplicate**. Library-shipped resolvers (§6 vanilla catalog) register via this path at client init; no existing call site changes.
- `extend` is **additive**: multiple extension resolvers per class allowed, registered in call order. The class does *not* need a primary registered first — extensions can stand alone if a consumer-owned class isn't registered as primary.
- `of(menu)` runs the primary resolver first (if any), then each extension in registration order, and merges the outputs under the collision policy in §2.

## 2. Collision policy — **consumer can only ADD categories; redefining existing is rejected**

When a resolver emits a `SlotGroupCategory` already present in the accumulated output (from the primary or an earlier extension), the duplicate entry is **dropped with a warn log**. Earlier entry wins. No exception thrown — parity with `register`'s first-wins-with-warn pattern.

Policy rationale:

- **Library-defined category meaning stays consistent across mods.** A consumer can't redefine what `FURNACE_INPUT` means for a vanilla menu. A decoration targeting `FURNACE_INPUT` gets the library's slot list, not a mod's replacement — cross-mod composability depends on this.
- **Consumer intent for the gap is "add a new category for my grafted slot,"** not "override a vanilla category." The policy matches the design intent stated in M8 §10.560 ("grafted-slot group tagged with a consumer-registered category") without opening a second door for category redefinition.
- **Cross-extension collisions** (two consumer mods both extending the same class, both emitting the same category) resolve the same way: first-extender-wins, later call warn-dropped. Symmetric with primary-vs-extension resolution; avoids a special case.

**What the policy does NOT forbid:** two extensions emitting *different* categories for the same slot. A slot can belong to multiple categories — `menu.slots.get(N)` could appear in both `PLAYER_INVENTORY` (primary) and `"mymod:custom_highlight"` (extension). That's expected and correct — categories are identity tags, not partitions.

## 3. Implementation sketch

Concurrency + log-level posture matches the existing `RESOLVERS` path:
- `RESOLVERS` (per existing code) is a plain `HashMap` — init-phase-only contract via Fabric's single-threaded init. `EXTENSIONS` uses the same, not `ConcurrentHashMap`.
- `register`'s success log is at `info` level. `extend`'s success log matches.

```java
private static final Map<Class<? extends AbstractContainerMenu>, SlotGroupResolver> RESOLVERS
        = new HashMap<>();
private static final Map<Class<? extends AbstractContainerMenu>, List<SlotGroupResolver>> EXTENSIONS
        = new HashMap<>();

public static <T extends AbstractContainerMenu> void extend(
        Class<T> menuClass, SlotGroupResolver resolver) {
    EXTENSIONS.computeIfAbsent(menuClass, k -> new ArrayList<>()).add(resolver);
    LOGGER.info("[SlotGroupCategories] extended resolver for {} (extension #{})",
            menuClass.getSimpleName(),
            EXTENSIONS.get(menuClass).size());
}

public static Map<SlotGroupCategory, List<Slot>> of(AbstractContainerMenu menu) {
    if (menu == null) return Map.of();
    Class<?> menuClass = menu.getClass();
    SlotGroupResolver primary = RESOLVERS.get(menuClass);
    List<SlotGroupResolver> extensions = EXTENSIONS.getOrDefault(menuClass, List.of());

    if (primary == null && extensions.isEmpty()) return Map.of();

    Map<SlotGroupCategory, List<Slot>> out = new HashMap<>();
    if (primary != null) out.putAll(primary.resolve(menu));
    for (SlotGroupResolver ext : extensions) {
        Map<SlotGroupCategory, List<Slot>> contribution = ext.resolve(menu);
        for (var e : contribution.entrySet()) {
            if (out.containsKey(e.getKey())) {
                LOGGER.warn("[SlotGroupCategories] extension for {} tried to redefine " +
                        "category {} — dropping", menuClass.getName(), e.getKey());
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
    }
    return Map.copyOf(out);
}
```

`Map.copyOf(out)` at return makes the result immutable — consumers can't mutate the registry's output. Matches existing `Map.of()` return on the empty path.

## 4. Consumer use at call site (V5.7 example)

```java
// In V5.7's client init — register an extension for the grafted slot.
SlotGroupCategory GRAFTED = new SlotGroupCategory("mkvalidator", "v5_7_grafted");

SlotGroupCategories.extend(FurnaceMenu.class, menu ->
        Map.of(GRAFTED, menu.slots.stream()
                .filter(s -> s.container == V5_7GraftedStorage.CONTAINER)
                .toList()));

// Then in V5.7's decoration scaffolding — standard SlotGroupPanelAdapter use.
new SlotGroupPanelAdapter(backdropPanel, SlotGroupRegion.TOP_ALIGN_RIGHT)
        .on(GRAFTED);
```

Consumer declares the category once, targets it via the same `.on(SlotGroupCategory...)` API that every other SlotGroupContext decoration uses. No M4-awareness at the adapter layer.

**Why container-reference filter, not `menu.slots.get(size - 1)`.** The slot list position of a grafted slot is not load-bearing — another mod grafting after this one would shift it. The stable identity of "my grafted slot" is its backing container reference (`V5_7GraftedStorage.CONTAINER`, a singleton the consumer owns). Filtering by container reference is correct in multi-mod coexistence; index-based selection is a brittle shortcut that copy-paste readers would inherit as a gotcha.

## 5. Test / verification shape

Programmatic probes in the V5.7 scenario (or a dedicated M8 contract test — TBD during implementation):

1. **Additive basic.** Extend a vanilla class with a new category → `of(menu)` returns primary's categories + the new one.
2. **Collision with primary.** Extend a vanilla class emitting a library-defined category (e.g., consumer emits `FURNACE_INPUT` at a different slot list) → primary's entry wins, warn logged, extension's version dropped.
3. **Cross-extension collision.** Two extensions on the same class emit the same category → first-extender-wins, warn logged.
4. **Multiple non-colliding extensions.** Two extensions emitting different categories → both merge in.
5. **Extension without primary.** Extend a class that has no `register` call → extension's output returned directly. (Used e.g., when a consumer owns a modded menu class and prefers `extend` over `register` for additive semantics.)
6. **Same slot in multiple categories.** Primary emits slot S in category A; extension emits slot S in category B → both categories present, both lists contain S. (Policy: not a collision — different keys.)
7. **Extend-without-primary with multiple extensions — call-order determinism.** Two extensions on a class with no registered primary, emitting different categories, registered in order A-then-B → `of(menu)` applies A before B. Same collision and merge rules as the with-primary path; the only difference is the absence of a primary layer. Made explicit so future readers don't have to derive determinism from Cases 4 + 5 composition.

Cases 1–4 can run as programmatic `/mkverify` assertions; 5–7 fall out of V5.7's scenario mechanics.

## 6. What does not change

- `register`'s first-wins semantics and warn behavior — unchanged. Existing library-shipped vanilla resolvers and consumer-owned modded-menu resolvers behave identically before and after this change.
- `SlotGroupPanelAdapter` API — unchanged. Consumers declare categories via the same `.on(SlotGroupCategory...)` call regardless of whether the category came from `register` or `extend`.
- Per-frame resolution cost — one extra map-iteration per registered extension. Negligible given extensions-per-class count is bounded by mod ecosystem size.
- M8 §5.3 exact-class resolution rule — unchanged. Extensions key on concrete class, same as primary.

## 7. Documentation follow-on (not part of the β commit)

After β ships and V5.7 lands green, close-out REPORT pass updates M8 §5.3 to describe `extend` alongside `register`, and §10.560's "consumer-registered category" line gets a cross-link to the `extend` API path. That's a close-out edit, not a β edit.

---

**Status: awaiting advisor review.** If approved, the β commit ships with (a) the `extend` method + chained `of` in `SlotGroupCategories.java`, (b) a short class-level javadoc describing the additive model and collision policy, (c) a `/mkverify` contract or V5.7-scenario probe for verification cases 1–4. V5.7 scaffolding then resumes, targeting FurnaceMenu with a consumer-registered category for the grafted slot.
