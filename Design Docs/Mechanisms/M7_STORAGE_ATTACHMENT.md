# M7 — Storage Attachment Taxonomy

**Phase 14b mechanism — persistence-shaped** (per `PHASES.md` §14b).

**Status: draft — round 1, awaiting advisor review.**

**Companion primitives:** `SlotIdentity` (M2, shipped), `PersistentContainerKey` (M1, shipped). M7 builds on M1's owner-key infrastructure — session identity is free; owner-keyed persistence of metadata is free (M1); M7 adds owner-keyed persistence of **slot-group content**.

**Absorbs:** Phase 12.5 finding #7 — `Phases/12.5/V5_7_FINDING_M1_STORAGE_WIRING.md`. M7 closes the gap between the library's `Storage` interface and its persistence story. The handler lifecycle will invoke save/load on storages; the `BlockEntityStorage` / `PlayerStorage` stubs get replaced by the M7 factories.

---

## 1. Purpose

Consumer mods need **slot-group content** — the actual ItemStacks in the slots — to persist on the natural owner of the slot group, with the same discipline M1 established for metadata. V5.7 surfaced the gap: a FurnaceMenu-grafted single-slot group holds an ItemStack within the session but loses it on disconnect, because the library's `Storage` interface has `save`/`load` methods that the handler lifecycle never calls. Consumer mods have been hand-rolling attachment wiring per-type (IP's `PlayerAttachedStorage` is 140 lines per container variant); each new consumer reinvents the shape.

M7 is the canonical "where slot-group content lives" story. It names the owner types (vanilla's six persistence surfaces plus a consumer extension), ships four v1 factories with uniform low per-entry cost, defers two (block-portable, entity-attached) to first-concrete-consumer trigger, wires the lifecycle the handler already owns, and deletes the per-consumer scaffolding.

Positioned against M1: M1 is *per-slot metadata* (small typed values, typically flags) keyed by `(owner, slot-index, channel-id)`. M7 is *per-slot-group content* (ItemStacks) keyed by `(owner, slot-group-id)`. They share the `PersistentContainerKey` resolution infrastructure and the "Fabric-attachment-on-natural-owner" pattern. They don't share storage shape — content is a fixed-length `NonNullList<ItemStack>`, not a channel-bag.

---

## 2. Consumer evidence

### IP — equipment + pockets panels (F8, F9)

IP's `PlayerAttachedStorage` wraps Fabric attachments (`EquipmentData`, `PocketsData`) to expose them as `Storage`. Implementation is ~140 lines of IP-private scaffolding per attachment type — factory + adapter + lifecycle hook. Phase 11 Layer 0 verified this works; it's also the kind of thing every persistence-hungry consumer will re-invent.

**Would adopt M7:** yes, once the library primitive lands. Migration replaces `PlayerAttachedStorage.forEquipment(...)` / `.forPockets(...)` / `.forPocketSlice(...)` with `StorageAttachment.playerAttached(...).bind(player)` + consumer-provided codec. Est. ~280 → ~30 lines of IP code.

### V5.7 (validator) — grafted FurnaceMenu slot

V5.7's Gate B narrowed to "within-session only" because cross-session persistence for a BlockEntity-scoped grafted slot had no library-provided path. Gate B (full) would have verified ItemStack survival across disconnect + reconnect + chunk unload/reload. Under M7, Gate B unnarrows — consumer declares `StorageAttachment.blockScoped(...)`, binds to the FurnaceBE at menu construction, slot group persists its single ItemStack with the BE's NBT.

**Would adopt M7:** yes. V5.7 becomes a Block-scoped regression probe.

### Shulker-palette — ruled out (per-item, not per-slot-group)

Shulker-palette's state is **per-item** (palette flag on each ItemStack). Uses `CUSTOM_DATA`. Per-item state is not M7's concern — M7 is slot-group-scoped content. SP stays as-is.

### Hypothetical — NOT evidence, listed only to stress-test the catalog

- A backpack mod placing a Player-attached slot group for carried items.
- A mod adding a furnace-side-car fuel cache.
- A mod shipping a travel trunk on donkeys.

These are **hypothetical consumer cases only** — used to think through whether the catalog covers realistic shapes, not to invoke Rule of Three. Rule-of-Three is already satisfied for the Player-attached case (IP × 3 attachments). Block-scoped needs one more beyond V5.7 to hit three; item-attached is satisfied by the existing ItemStackStorage use. See §4.1 for the Principle 11 per-entry check.

---

## 3. Scope

- **Slot-group-scoped content persistence** via Fabric attachments (for Block / Entity / Player owner types), DataComponents (for Item-attached), or ephemeral in-memory (for session-scope-only).
- **Four v1 owner types + modded extension point; two deferred.** Catalog v1-coverage check per Principle 11's per-entry framing is in §4.1.
- **Server-authoritative persistence.** Mutations (item inserted / removed / dropped) happen via vanilla's slot-click protocol → `Container.setItem(slot, stack)` → M7's Storage-backed container adapter marks the attachment dirty. The vanilla sync protocol handles player-visible updates.
- **Per-player private is not M7's concern.** Slot-group content is shared — the item in a chest is the chest's item. M1's per-player-private model applies only to metadata.
- **Vanilla-inspectable.** Stored content is NBT-native; `/data get` on the owner returns the attachment's serialized form.
- **Library handler lifecycle owns binding + save invocation.** `MenuKitScreenHandler` calls `storage.save(...)` on menu close for Persistent storages; `storage.load(...)` on menu open. Vanilla's existing BE/Player save pipelines handle the wire — M7's save/load invocations feed into them.

---

## 4. Design decisions

Each decision is a draft position. Advisor review may push back; the shape of §4.1, §4.2, and §4.3 is load-bearing for the rest of the doc.

### 4.1 Owner type catalog — four v1 types + consumer extension; two deferred

Vanilla has six persistence surfaces; v1 ships four of them with uniform low per-entry cost. Two (block-portable, entity-attached) carry distinct risk beyond "one more factory" and defer to first-concrete-consumer triggering.

#### v1 — ships

| Type | Owner | Persistence surface | Examples |
|---|---|---|---|
| **Ephemeral** | — (session-scoped) | None; memory only | Crafting grid inputs, result slots, anvil/grindstone inputs |
| **Block-scoped (dies-with-block)** | `BlockEntity` | Attachment on BE, serialized with BE NBT; breaks with block | Chest, barrel, trapped chest, hopper, dispenser, dropper, furnace family, brewing stand |
| **Player-attached** | `Player` | Attachment on Player, serialized with player save data | Player inventory (vanilla-owned; M7 doesn't replace), ender chest, IP equipment, IP pockets |
| **Item-attached** | `ItemStack` (via `DataComponentType`) | DataComponent on stack; travels with the item across menus / drops / pickups | Bundle (`DataComponents#BUNDLE_CONTENTS`), shulker items when carried |
| **Modded extension** | consumer-defined | consumer-provided save hooks | Anything outside the four above + the deferred two |

#### Deferred — ship when first concrete consumer surfaces

| Type | Why deferred |
|---|---|
| **Block-portable (item-form-traveling)** — e.g., shulker box | §10 Q5: requires a Fabric-API hook for BE↔item attachment copy. If the hook doesn't exist at a reasonable shape, shipping requires a vanilla-code-path-ownership mixin (Principle 1 concern). Resolve-before-commit; no concrete consumer today. |
| **Entity-attached** — donkey / horse / llama / minecart-with-chest / minecart-with-hopper | Mount-death edge cases (items drop as item entities), dimension-crossing edge cases (minecarts can cross, other entities generally don't), entity despawn edge cases. Design surface benefits from real consumer evidence; no concrete consumer today. |

Both deferred types can adopt M7 additively once they surface — factory registration is small per-entry work, framework is already in place from v1.

#### Principle 11 — per-entry check

**Per-entry cost (v1 types):** one factory method each, 20-40 lines plus shared infrastructure. Uniform low cost — no entry carries risk beyond "register an attachment and expose a Storage view."

**Per-entry cost (deferred types):**
- Block-portable: distinct risk (Q5 may force vanilla-mixin, violating Principle 1).
- Entity-attached: distinct design surface (edge cases above) that existing M7 v1 types don't have analogues for.

Neither deferred type satisfies the "cheap to add" half of the exception at the per-entry level. Rule of Three applies; defer.

**Incompleteness cost (v1 types):** high — a consumer needing one of the four v1 types has no library path without hand-rolling the IP-style 140-line-per-type scaffolding. Migration cost on later catch-up is real.

**Incompleteness cost (deferred types):** same high migration cost, but the absence of concrete consumers today means no migration is being forced yet. Waiting for first concrete consumer gives better design evidence.

Both tests satisfied for the v1 types → exhaustive-at-v1 applies to that subset. Not applied to the deferred types → Rule of Three applies.

**Catalog-level vs per-entry framing (M6 §6 comparison).** M6 invoked the exception at catalog level because its 43 vanilla categories had uniform per-entry cost (one enum + one resolver, no risk variation). M7's six vanilla types don't have uniform per-entry cost — blockPortable and entityAttached carry distinct risk. The per-entry check is the right granularity here, consistent with Principle 11's "both tests must hold" framing.

### 4.2 API shape — `StorageAttachment<Owner, Content>`

```java
public final class StorageAttachment<O, C> {

    // ── Library-shipped factories ────────────────────────────────────────

    public static <C> StorageAttachment<Void, C> ephemeral(
            int slotCount, Codec<C> codec, Supplier<C> defaultFactory);

    public static <C> StorageAttachment<Player, C> playerAttached(
            String namespace, String path,
            int slotCount, Codec<C> codec, Supplier<C> defaultFactory);

    public static <C> StorageAttachment<BlockEntity, C> blockScoped(
            String namespace, String path,
            int slotCount, Codec<C> codec, Supplier<C> defaultFactory);

    public static <C> StorageAttachment<ItemStack, C> itemAttached(
            DataComponentType<C> componentType,
            Supplier<C> defaultFactory);
    // ↑ Backed by DataComponents, not Fabric attachments. Component travels
    //   with the stack through every menu, pickup, drop, throw. Generalizes
    //   the existing ItemStackStorage (which hardcodes DataComponents.CONTAINER).

    public static StorageAttachment<ItemStack, ItemContainerContents> itemContainer(
            int slotCount);
    // ↑ Convenience specialization: itemAttached pre-wired with the vanilla
    //   CONTAINER component + ItemContainerContents codec. Replaces direct
    //   use of ItemStackStorage for shulker / bundle-style consumers.

    // blockPortable + entityAttached are deferred to future work — see §4.1.

    // ── Modded extension ────────────────────────────────────────────────
    //   Consumer provides save hooks. See §4.4.

    public static <O, C> StorageAttachment<O, C> custom(
            CustomAttachmentSpec<O, C> spec);

    // ── Consumer usage surface ──────────────────────────────────────────

    /**
     * Binds this attachment to a specific owner instance. Returns a
     * {@link Storage} view that reads/writes the attachment's content for
     * that owner. Handler lifecycle holds the binding for the menu's
     * lifetime; storage save/load fires automatically.
     *
     * <p>For owners with stable references during the menu lifetime
     * (Player, BlockEntity, Entity), pass the reference directly.
     */
    public Storage bind(O owner);

    /**
     * Binds via a live supplier for owners whose reference can change
     * during the menu lifetime (notably ItemStack, which vanilla may
     * replace during interactions — see {@link ItemStackStorage}'s existing
     * Supplier pattern). Library calls the supplier on every read/write.
     */
    public Storage bind(Supplier<O> ownerSupplier);
}
```

**Why `Content` is generic, not fixed to `NonNullList<ItemStack>`:**
Slot-group content is usually `NonNullList<ItemStack>` but consumers may want structured payloads — a bundle's contents are `List<ItemStack>` (variable length), a fuel-cache slot is a `FuelStack` record (ItemStack + burn-time remaining). Generic keeps the primitive honest about its purpose: "persist this data on this owner." The slot-group binding wraps the content into a `Storage`; if content isn't `ItemStack`-shaped, the consumer supplies the wrapper.

**Structured-content wrapper pattern (for non-ItemStack content):**
When `Content` is a structured record rather than a plain `NonNullList<ItemStack>`, the consumer supplies a two-way adapter that `bind(...)` composes between Storage and the attachment:

```java
// Consumer-declared wrapper for a FuelStack(ItemStack stack, int burnTimeRemaining) payload
StorageView<FuelCache, FuelStack> view = new StorageView<>() {
    public ItemStack toDisplayStack(FuelStack content, int slotIdx) {
        return content.stack();  // what the slot renders in the UI
    }
    public FuelStack applyMutation(FuelStack content, int slotIdx, ItemStack newStack) {
        // Vanilla mutation path delivers a new ItemStack; consumer folds it
        // back into the structured content, preserving non-ItemStack fields.
        return new FuelStack(newStack, content.burnTimeRemaining());
    }
};
Storage storage = FUEL_CACHE.bind(owner, view);
```

For the `NonNullList<ItemStack>` default case, no view is needed — `bind(owner)` returns a Storage directly. The view overload is the escape hatch for structured content. Concrete API shape finalizes during implementation; one variant reserved.

**Default-factory parameter** (`Supplier<C> defaultFactory`): called when the attachment is first read on an owner that has no stored value. Consumer-supplied so `NonNullList.withSize(N, ItemStack.EMPTY)` gets its N passed correctly.

**Codec is `Codec<C>`, not `Codec<C> + StreamCodec<C>`.** Unlike M1 (which has a metadata-sync protocol requiring wire encoding), M7's content reaches the client via vanilla's slot-sync protocol — individual slot updates travel as packets vanilla already owns. M7 just needs persistence codec, not wire codec.

### 4.3 Relationship to M1 — layered, not merged

Both mechanisms persist per-slot data on the natural owner. They're architecturally **layered**, not unified:

| | M1 | M7 |
|---|---|---|
| **Data** | Metadata (Tag per channel per slot) | Content (per-slot-group, typically ItemStack list) |
| **Shape** | `Map<channelId, Map<slotIdx, Tag>>` | `Codec<Content>` |
| **Key** | `(owner, slotIdx, channelId)` | `(owner, slot-group-id)` |
| **Sync** | Custom snapshot + update packets | Vanilla's slot-sync protocol |
| **Size** | Small (flags, small structured values) | Bulky (ItemStacks, possibly many) |
| **Registration** | Channel at mod init | Attachment spec at mod init |
| **Lifecycle** | Attachment save/load | Attachment save/load (same infrastructure, parallel ownership) |

**Shared infrastructure:**
- `PersistentContainerKey` resolution (M1's `ContainerKeyResolver`). M7 uses the same key variants (`PlayerInventory`, `EnderChest`, `BlockEntityKey`, `EntityKey`, `Modded`) plus an additional `ItemKey` variant for item-attached storage.
- Fabric attachment registration pattern (M1 registers one bag attachment per owner type; M7 registers one content attachment per spec).
- NBT-native serialization via `NbtOps`.

**Separate concerns:**
- **Snapshot sync.** M1 has its own snapshot packet because metadata changes don't ride vanilla's slot-sync. M7 content rides slot-sync automatically — no custom snapshot needed.
- **Storage shape.** M1's bag is sparse (slots without state don't appear); M7's content is dense (every slot has a value, even if EMPTY).
- **Registration ergonomics.** M1 channels are lookup-by-Identifier (consumer can register once and call `SlotStateChannel<T>.get(slot)` anywhere). M7 bindings are owner-instance-scoped (consumer calls `attachment.bind(owner)` at menu construction to produce the Storage the slot-group uses) — a per-menu activity, not a lookup.

**Could M1 be expressed as a `StorageAttachment<Owner, SlotStateBag>`?** Technically yes — the owner-attachment pattern generalizes. But M1's typed-channel ergonomics (register once, call anywhere) depend on having a channel registry keyed by Identifier; those ergonomics would be lost if M1 became "ask for a SlotStateBag at menu construction and index into it." The two APIs have different consumer touchpoints. Keep them layered; share the infrastructure; don't force a single surface.

**Gap-closure stance for Phase 12.5 #7.** The V5.7 finding named the storage-wiring gap. M7 is the resolution: the library's `Storage` interface exists; the Phase-3-TODO `BlockEntityStorage` and auto-wired `PlayerStorage` disappear (or become thin facades over M7 factories); the handler lifecycle invokes `save`/`load` on every Persistent storage at menu close/open. V5.7's Gate B unnarrows once M7 ships — see §9.2.

### 4.4 Consumer extension point — `CustomAttachmentSpec<O, C>`

`CustomAttachmentSpec` serves two purposes:

1. **Modded owner types.** Consumers whose owner type isn't covered by the four v1 factories (or the two deferred ones when they ship) declare a custom spec providing their own save/load hooks.
2. **Decorator-path escape hatch.** Consumers decorating vanilla menus via `ScreenPanelAdapter` — which doesn't route through `MenuKitScreenHandler` — use `CustomAttachmentSpec` to own the save/load lifecycle themselves. This is the deliberate escape hatch from the handler-owned default path (see §10 Q7 below).

A consumer using `CustomAttachmentSpec` provides:

```java
public interface CustomAttachmentSpec<O, C> {

    /** How to serialize the content. */
    Codec<C> codec();

    /** How to produce a default content when no stored value exists. */
    Supplier<C> defaultFactory();

    /**
     * Read the stored content for this owner instance. Returns empty if
     * no value has been persisted yet.
     */
    Optional<C> read(O owner);

    /** Write the content for this owner instance. */
    void write(O owner, C content);

    /** Mark the owner dirty so the vanilla save pipeline persists changes. */
    void markDirty(O owner);

    /** Identifier used for debugging + /data get path construction. */
    Identifier id();
}
```

The library provides no automatic lifecycle for custom specs — the consumer owns save/load wiring. The library's role is only to call `read` / `write` / `markDirty` at the right points in the handler lifecycle. This matches M1's modded extension pattern (consumer provides resolver + attachment binding; library threads payload through).

**Example — WorldSavedData-backed regional storage (hypothetical):**

```java
StorageAttachment<RegionKey, NonNullList<ItemStack>> REGIONAL =
    StorageAttachment.custom(new CustomAttachmentSpec<>() {
        public Codec<NonNullList<ItemStack>> codec() {
            return NonNullList.codec(ItemStack.CODEC, 9);
        }
        public Supplier<NonNullList<ItemStack>> defaultFactory() {
            return () -> NonNullList.withSize(9, ItemStack.EMPTY);
        }
        public Optional<NonNullList<ItemStack>> read(RegionKey key) {
            return MyMod.REGION_DATA.getSaved(key);
        }
        public void write(RegionKey key, NonNullList<ItemStack> items) {
            MyMod.REGION_DATA.save(key, items);
        }
        public void markDirty(RegionKey key) {
            MyMod.REGION_DATA.setDirty();
        }
        public Identifier id() {
            return Identifier.of("my-mod", "regional_stash");
        }
    });
```

The library never learns what `RegionKey` is. Consumer owns persistence entirely; library only dispatches lifecycle calls.

### 4.5 V5.7 finding #7 resolution

**Before (Phase 12.5 shape):**
- `Storage` interface defines `save(ValueOutput)` / `load(ValueInput)` via `PersistentStorage`.
- Handler lifecycle never invokes save/load.
- `BlockEntityStorage` has a Phase-3 TODO: writes to a `fallback[]` array, `markDirty()` is a no-op.
- `PlayerStorage` is fully-implemented but also not auto-wired.
- Consumers (IP) bypass the library and hand-roll attachments via plain `Storage`.

**After (Phase 14b M7 shape):**
- `StorageAttachment<O, C>` is the consumer touchpoint. v1 ships four factories (ephemeral, playerAttached, blockScoped, itemAttached + itemContainer convenience) plus the custom-spec extension; two more (blockPortable, entityAttached) defer per §4.1.
- `MenuKitScreenHandler` holds the bindings for its menu's slot-groups. On menu close (via `removed(Player)`), invokes `storage.save(player.level().registryAccess())` on each Persistent-backed slot-group. On menu open (handler construction), invokes `storage.load(...)` to rehydrate session state from the attachment.
- `BlockEntityStorage` (the stub): **deletes.** Consumers use `StorageAttachment.blockScoped(...)` instead. The Phase-3 TODO dissolves because its implementation is replaced, not completed.
- `PlayerStorage`: **deletes** for the same reason, replaced by `StorageAttachment.playerAttached(...)`.
- `EphemeralStorage`: **stays** as-is. Session-scoped, no persistence — already correct. M7's `StorageAttachment.ephemeral(...)` becomes a thin wrapper returning an `EphemeralStorage`-backed `Storage`.
- `ItemStackStorage`: **deletes.** Replaced by `StorageAttachment.itemContainer(int)` — the convenience factory that pre-wires vanilla's `DataComponents.CONTAINER` + `ItemContainerContents` codec (§4.2 Q2 resolution).
- IP's `PlayerAttachedStorage` (140 lines × 3 attachment types): **deletes.** IP declares `StorageAttachment.playerAttached(...)` per attachment in an `IPStorageAttachments` class and calls `.bind(player)` at menu construction. Net: ~420 lines → ~60 lines. Side benefit: IP stops bypassing the library's Persistent surface.

V5.7's Gate B unnarrows: the Block-scoped grafted slot's ItemStack now survives disconnect/reconnect/chunk-unload because `StorageAttachment.blockScoped(...)` binds to the FurnaceBE and the lifecycle fires save/load correctly. The V5.7 scenario javadoc updates in the Phase 14b migration commit; the scenario itself needs no code change beyond swapping `EphemeralStorage.of(1)` for the new M7 factory.

### 4.6 Sync + lifecycle

#### Menu open (client-invisible; server fully handles)

```
Server: player.openMenu(...) → handler construction
         ↓ handler constructor calls attachment.bind(owner) for each slot group
         ↓ binding's load() runs — reads attachment, populates internal content array
         ↓ vanilla open-screen packet ships to client as usual
         ↓ first slot-sync packet carries the loaded ItemStacks (vanilla protocol)
         ↓ client sees slots populated — no M7 work on the client side
```

#### Menu mutation (vanilla-driven)

Client clicks slot → vanilla `ClickMenuC2SPayload` → server mutates via `Container.setItem(idx, stack)` → M7's `StorageContainerAdapter` calls `markDirty(owner)` → vanilla save pipeline catches the next write.

No custom M7 mutation packets. Vanilla owns the wire entirely.

#### Menu close

```
Server: handler.removed(Player)
         ↓ handler iterates slot groups, calls storage.save() on each Persistent-backed one
         ↓ save() writes the current content array back to the attachment
         ↓ vanilla save pipeline takes it from there (BE NBT on chunk save,
           player data on disconnect, entity NBT on chunk save, etc.)
```

**Optimization:** `save()` can short-circuit if the storage wasn't mutated during the session (dirty flag on the storage adapter). Avoids pointless NBT work on read-only interactions.

#### Block break (block-scoped v1)

Block-scoped: on block break, the library drops attachment content as item entities via the vanilla drop-contents pattern, then the BE is destroyed. Matches vanilla container-block semantics (THESIS Principle 2 vanilla-substitutability) — consumers have internalized "break container block → items drop" from vanilla chests/furnaces, and M7's blockScoped factory fulfills that same contract.

**Implementation:** library-shipped mixin at `BlockEntity.preRemoveSideEffects(BlockPos, BlockState)` HEAD. This is 1.21.11's canonical pre-removal side-effects hook — the same method vanilla's `BaseContainerBlockEntity` overrides to dispatch `Containers.dropContents` for its own inventory. Running at HEAD means M7 contents drop alongside the vanilla BE inventory, in the same frame, with the same physics. HEAD injection is observational — no vanilla flow change, clean Principle 1 hygiene. Server-side only (client-side block-removal predictions don't spawn ghost entities).

*(1.21.11 note: the old `Block.onRemove(BlockState, Level, BlockPos, BlockState, boolean)` hook was split into `Block.affectNeighborsAfterRemoval` (redstone updates) and `BlockEntity.preRemoveSideEffects` (inventory drops etc.). Targeting the BE-level hook is the clean choice — the BE is guaranteed present, the `Level` is accessible via `be.getLevel()`, and no subclass-override-chain ambiguity exists.)*

**Consumers wanting silent-loss behavior** opt out via `StorageAttachment.custom(spec)` instead of `blockScoped(...)`. Only blockScoped attachments enroll in the drop-on-break registry; custom specs own their own lifecycle entirely.

**Surfaced during Phase 14b smoke test.** Pre-advisor-review v1 documented block-scoped content as "lost on break — intended, the block's gone." Consumer-facing UX testing caught that this diverges from vanilla's container-block contract, surfacing a contract under-specification (THESIS Principle 7 validate-the-product in action). Corrected before Phase 14b ship.

#### Player death

Player attachment persists across death (serialized with player data, reattached on respawn) — matches vanilla semantics (player keeps inventory unless gamerules dictate otherwise). The M1 respawn snapshot path (see M1 §6.2a) re-delivers player-attached content to the newly-constructed InventoryMenu; M7's player-attached content rides the same vanilla slot-sync that repopulates the inventory panel post-respawn.

#### Cross-dimensional (v1)

Player attachments travel with the player across dimensions. Block entities are dimension-scoped and don't cross (a chest exists in exactly one dimension; moving is destroy + create which resets state). Item-attached storage travels with the item wherever the item goes.

---

**Deferred-type behavior** — documented here for continuity, behavior NOT shipping in v1. Named so the design intent is captured when the deferred types are reconsidered.

##### Block break — block-portable (deferred)

Block-portable would extend block-scoped: on BE destroy, copy the attachment's content into the item form's `BlockEntityTag`; on placement, restore. The `blockPortable` factory would register these hooks. Ships when first concrete consumer surfaces AND §10 Q5 resolves (Fabric-API hook shape for BE↔item attachment copy — no vanilla-code-path mixin required, per Principle 1).

##### Entity death — entity-attached (deferred)

Entity-attached would destroy the attachment when the entity dies (donkey killed → saddle-bag attachment destroyed → content lost; gameplay layer handles the item-entity drops if the mod wants that behavior). Minecart dimension-crossing would preserve attachments (minecarts can cross dimensions; other entities generally can't). Edge cases (entity despawn, entity teleport, chunk unload carrying entity) want real consumer evidence before commit.

---

## 5. Consumer API — before / after

### 5.1 IP equipment + pockets (migration)

**Before** (~420 lines across attachments/ package):

```java
// PlayerAttachedStorage.java — plain-Storage adapter over Fabric attachments, ~140 lines
public static Storage forEquipment(Player player) { ... }
public static Storage forPockets(Player player) { ... }
public static Storage forPocketSlice(Player player, int pocketIdx) { ... }

// EquipmentData.java — Fabric attachment, save/load, sync, ~90 lines
// PocketsData.java — Fabric attachment, save/load, sync, ~120 lines
// IPPlayerAttachments.java — registration, ~35 lines
// (plus network payloads for sync — each ~30 lines)
```

**After:**

```java
// inventory-plus/storage/IPStorageAttachments.java — ~60 lines total

public final class IPStorageAttachments {
    public static final StorageAttachment<Player, NonNullList<ItemStack>> EQUIPMENT =
        StorageAttachment.playerAttached(
            "inventory-plus", "equipment",
            /* slotCount */ 10,
            NonNullList.codec(ItemStack.CODEC, 10),
            () -> NonNullList.withSize(10, ItemStack.EMPTY));

    public static final StorageAttachment<Player, NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached(
            "inventory-plus", "pockets",
            /* slotCount */ 27,  // 3 pockets × 9 slots
            NonNullList.codec(ItemStack.CODEC, 27),
            () -> NonNullList.withSize(27, ItemStack.EMPTY));
}
```

Menu-construction site:
```java
Storage equipment = IPStorageAttachments.EQUIPMENT.bind(player);
panel.slotGroup(IPIds.EQUIPMENT)
     .cols(5).rows(2)
     .build(equipment);
```

Deletes: `PlayerAttachedStorage`, `EquipmentData`, `PocketsData`, `IPPlayerAttachments`, every sync packet (vanilla slot-sync handles it).

### 5.2 V5.7 (migration)

**Before:**
```java
// V5_7GraftedStorage.java
public static final Storage STORAGE = EphemeralStorage.of(1);
public static final Container CONTAINER = new StorageContainerAdapter(STORAGE);
```

**After:**
```java
public static final StorageAttachment<BlockEntity, NonNullList<ItemStack>> GRAFTED =
    StorageAttachment.blockScoped(
        "mkvalidator", "v5_7_grafted",
        /* slotCount */ 1,
        NonNullList.codec(ItemStack.CODEC, 1),
        () -> NonNullList.withSize(1, ItemStack.EMPTY));
```

At menu construction (inside the V5.7 FurnaceMenu mixin):
```java
Storage grafted = V5_7GraftedStorage.GRAFTED.bind(thisBE);
this.addSlot(new MenuKitSlot(grafted, 0, SLOT_X, SLOT_Y, ...));
```

V5.7's Gate B unnarrows; V5.7 becomes a canonical block-scoped regression probe.

### 5.3 Decorator-path example (CustomAttachmentSpec as escape hatch)

Consumer decorating a vanilla screen via `ScreenPanelAdapter` — no MenuKit handler involved — uses `CustomAttachmentSpec` and wires save/load on the **server-side** menu-close hook, not the client-side screen hook.

**Why server-side.** Fabric attachments live server-side. `Screen.removed()` fires client-side and can't persist; writing to an attachment from the client thread is a no-op for save purposes. The correct save trigger is `AbstractContainerMenu.removed(Player)`, which fires server-side when vanilla processes the client's `ServerboundContainerClosePacket`.

```java
// Consumer holds the spec directly — both the spec and the StorageAttachment
// wrapping it. The spec is the manual-lifecycle surface; the StorageAttachment
// is the bind-to-produce-Storage surface. Use whichever shape you need.
public static final CustomAttachmentSpec<Player, NonNullList<ItemStack>> MY_SIDECAR_SPEC = ...;
public static final StorageAttachment<Player, NonNullList<ItemStack>> MY_SIDECAR =
    StorageAttachment.custom(MY_SIDECAR_SPEC);

// Consumer mixin — server-side — on the vanilla handler class
@Mixin(InventoryMenu.class)
public abstract class MyInventoryMenuMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void myMod$saveOnClose(Player player, CallbackInfo ci) {
        // Fires server-side when the player closes the inventory.
        // Content snapshot here is the authoritative server state.
        NonNullList<ItemStack> content = MyMod.CURRENT_SIDECAR_CONTENT.get(player);
        MY_SIDECAR_SPEC.write(player, content);
        MY_SIDECAR_SPEC.markDirty(player);
    }
}
```

**Corresponding load trigger** — the decorator-path consumer also arranges for the content to be loaded into the client's session cache before the player interacts. Two viable shapes:

- **Load server-side at menu construction** (in a `@Mixin(InventoryMenu.class)` `<init>` hook) → send a custom S2C packet carrying the content → client populates a mod-local cache → decorator panel renders from that cache.
- **Rely on vanilla's slot-sync if the content is tied to existing vanilla slots** — e.g., a decorator panel that visualizes slot state but doesn't add new slots.

Decorator-path persistence is genuinely harder than handler-owned (which is why the default path is default). The library's role is to expose the `write` / `markDirty` primitives; the consumer owns the server/client routing.

The `CustomAttachmentSpec` surface gives consumers full manual control — exactly as documented in §4.4's "decorator-path escape hatch" framing.

---

## 6. Library surface

**New files:**

- `core/StorageAttachment.java` — public API. Static factories + `bind(owner)` method. Internally dispatches to the appropriate backing strategy.
- `core/attachment/StorageAttachments.java` — internal: attachment registry + CACHE + block-scoped enrollment for drop-on-break dispatch.
- `core/attachment/CustomAttachmentSpec.java` — public: consumer-implemented interface for modded extension + decorator escape hatch.
- `core/attachment/BlockScopedDropHandler.java` — internal: called by the drop-on-break mixin to iterate a BE's block-scoped attachments and drop item entities.
- `mixin/M7BlockDropMixin.java` — library mixin at `BlockBehaviour.BlockStateBase.onRemove` HEAD that dispatches drop-on-break.

**Modified files:**

- `core/Storage.java` — javadoc refresh (deleted-stub references removed, `StorageAttachment` referenced as the canonical persistent-storage entry point). Interface unchanged.
- `screen/MenuKitScreenHandler.java` — javadoc refresh to show M7-based builder example. No runtime lifecycle hooks needed: Fabric attachments auto-persist via Codec on the owner's save path, so the library never explicitly invokes save/load — reads/writes flow through `attachment.bind(owner)` directly.
- `menukit.mixins.json` — registers `M7BlockDropMixin` in the `mixins` array (both sides; the mixin gates on server-side internally).

**Deleted files:**

- `core/BlockEntityStorage.java` — replaced by `StorageAttachment.blockScoped(...)`. The Phase-3 TODO stub dissolves (implementation replaced, not completed).
- `core/PlayerStorage.java` — replaced by `StorageAttachment.playerAttached(...)`.
- `core/ItemStackStorage.java` — replaced by `StorageAttachment.itemContainer(int)` convenience specialization (Q2 resolution). The CONTAINER-backed pattern ItemStackStorage implements becomes the v1 default for item-attached consumers.
- IP: `PlayerAttachedStorage.java`, `EquipmentData.java`, `PocketsData.java`, `IPPlayerAttachments.java`, related sync payloads.

**Unchanged:**

- `SlotIdentity`, `PersistentContainerKey`, `SlotStateChannel` (M1's typed channels), `EphemeralStorage`, `ReadOnlyStorage`, `VirtualStorage`, `state/ContainerKeyResolver` (item-attached bypasses PCK per §10 Q1 — no new variant needed).

**Not in v1 (deferred per §4.1):** `BlockPortableStrategy`, `EntityAttachmentStrategy`. Add when first concrete consumer triggers the corresponding factory.

---

## 7. Migration plan — Phase 14b + 15a

**14b: ship library primitive + delete stubs + V5.7 migration — single commit.** Per Phase 14a pattern (library delete + consumer migration landed together, no interim broken-build state):
- Build `StorageAttachment` + v1 strategies (ephemeral, playerAttached, blockScoped, itemAttached, itemContainer, custom).
- Wire `MenuKitScreenHandler` lifecycle (save on `removed`, load at construction).
- Delete `BlockEntityStorage` + `PlayerStorage` + `ItemStackStorage` stubs.
- Migrate V5.7's grafted-slot storage from `EphemeralStorage.of(1)` to `StorageAttachment.blockScoped(...)`.
- `/mkverify` aggregator runs V5.7 under the new primitive (Gate B unnarrows). M7 verification probe added (§8).

**15a: IP migration.** Migrate IP's equipment + pockets attachments to M7. Phase 15a consumer work per PHASES.md — not in 14b because 14b is library-scope and IP migration is consumer-scope.

---

## 8. Verification plan

### 8.1 `/mkverify` aggregator probe — M7 round-trip

New validator scenario (or extension to V5.7): **V5.8 — M7 cross-session probe.**

- Register `StorageAttachment.blockScoped(...)` for a test 1-slot group on a test BE.
- Place the test BE (or synthesize one via the test harness).
- Write a test ItemStack to the slot via the standard Storage write path.
- Force attachment save (invoke close on the test menu, or trigger attachment serialization directly via the Fabric API).
- Clear the in-memory attachment (simulating chunk unload).
- Force attachment load (simulating chunk reload).
- Read slot → expect the same ItemStack.

Verification runs programmatically via `runAggregated` — no live screen. Asserts:
1. Content survives save/load round-trip.
2. `/data get` on the BE shows the attachment's NBT.
3. Storage's `markDirty` correctly triggers BE-dirty propagation (assert via vanilla dirty-flag inspection).

### 8.2 Integration-level (dev client)

- **V5.7 full Gate B.** Grafted slot holds an ItemStack; disconnect; reconnect; open furnace; ItemStack still there.
- **Block break.** Place BE with content; break block; place new BE same position; slot is empty. PASS.
- **Chunk unload + reload.** Content survives unload + return. PASS.
- **IP equipment persistence.** Post-15a migration: equipment survives disconnect/reconnect. Already known-good behavior pre-migration; verification confirms no regression.

*(Shulker portability check deferred with `blockPortable` factory per §4.1. Entity mobility across dimensions deferred with `entityAttached`.)*

### 8.3 Principle-6 inspection test

After writing any value, assert `/data get entity @p` (for player-attached) or `/data get block X Y Z` (for block-scoped) shows the M7 attachment's NBT under the library namespace. Confirms THESIS Principle 6 holds empirically.

---

## 9. Library vs consumer boundary

**M7 provides:**
- The four v1 factories (ephemeral, playerAttached, blockScoped, itemAttached + itemContainer convenience) + custom-spec mechanism.
- Attachment registration machinery (one library-owned attachment per factory instantiation).
- Handler lifecycle wiring for save/load invocation (default path).
- Manual save/load primitives via CustomAttachmentSpec (decorator path).
- NBT-native persistence via the consumer's `Codec<C>`.

**Consumers provide:**
- Codec for their content type.
- Default-factory for empty state.
- Slot-group binding at menu construction (`attachment.bind(owner)`).
- For custom specs: save/load hooks, identity, dirty-marking.

**Library does NOT provide:**
- Pre-built attachments (no `StorageAttachment.VANILLA_CHEST_SIDECAR` or similar).
- Mutation-sync packets (vanilla's slot-sync handles this).
- Authorization or access control (per-slot-group content is shared, not per-player-private).
- Automatic migration from pre-M7 storage shapes (consumers migrate manually).
- Content validation or shape-enforcement (consumer's codec is authoritative).

---

## 10. Open design questions — round 1

1. **Item-attached storage — uses `PersistentContainerKey.ItemKey`, or bypasses PCK entirely?**
   DataComponents are already owner-attached (they travel with the ItemStack via vanilla's component system). M1's `PersistentContainerKey` architecture doesn't naturally include "the current ItemStack" as an owner because ItemStacks don't have stable UUIDs across copy / drop / pickup. Two options:
   - **(a) Add `ItemKey` variant.** Keys by the component type id. Works mechanically but is weird — the "key" doesn't identify a specific stack, it identifies the fact that "whatever stack this is, read its component under type X."
   - **(b) Bypass PCK.** Item-attached is the one owner type that doesn't need PCK — the `DataComponentType<C>` handle is sufficient. Factory returns a Storage that reads the current slot's stack and fetches its component directly.
   **My pull: (b).** PCK is for "resolve this container to its persistent owner"; DataComponents are already resolved. Forcing them into PCK manufactures complexity.

2. **`ItemStackStorage` disposition — RESOLVED during drafting.**
   Read [ItemStackStorage.java](menukit/src/main/java/com/trevorschoeny/menukit/core/ItemStackStorage.java) post-draft. It's item-attached backed by vanilla's `DataComponents.CONTAINER` (the shulker/bundle component). Implements `PersistentStorage` with `populate` / `syncToItem` / save / load. Uses `Supplier<ItemStack>` for the live-reference pattern — confirms the two-form `bind(O)` / `bind(Supplier<O>)` shape in §4.2 is right.
   
   **Disposition:** becomes a thin facade (or deletes entirely) in favor of `StorageAttachment.itemContainer(size)` — a CONTAINER-specialized factory that pre-wires the generic `itemAttached(CONTAINER, ItemContainerContents.CODEC, ...)` pattern. Consumers wanting a custom DataComponent go through the generic factory; consumers wanting the vanilla CONTAINER component get the ergonomic helper.
   
   One-line update to §4.2 signatures added; shown above as `itemContainer(int slotCount)`.

3. **Binding-time vs registration-time attachment.**
   `StorageAttachment.playerAttached(...)` registers a Fabric attachment at mod init. Consumers that declare N distinct attachments register N Fabric attachments. Is that OK? Or should the library register one "content-bag" attachment per owner type and multiplex via a `Map<Identifier, Content>` inside (parallel to M1's single-bag-with-channels)?
   - **Per-attachment (current draft shape):** simpler attachment logic; slightly more Fabric attachment overhead; each consumer's data is a separate NBT subtree.
   - **Multiplexed:** one registration, tighter NBT payload, harder attachment identity (all mods' data in one bag — less `/data get` legibility per mod).
   **My pull: per-attachment.** Principle 6 (inspectable NBT) prefers per-mod subtrees. Fabric's attachment registration is cheap.

4. **`MenuKitScreenHandler.removed` hook — where does save() fire?**
   Vanilla's `AbstractContainerMenu.removed(Player)` fires when the player closes the menu. Appropriate save hook. But: for shared containers (two players viewing the same chest), the save should fire on the *last* player closing, not the first. Vanilla handles this at the BE level (BE doesn't drop its inventory until the last viewer disconnects). M7's save can simply be idempotent — saving the current content state every time a viewer closes is fine; the content is already what it is. Confirm this interpretation, or do we need to track viewer count?
   **My pull: idempotent save per close is fine.** Vanilla's slot-sync already guarantees content consistency across viewers; the M7 attachment just stores whatever the current state is. No viewer-count tracking needed.

5. **Block-portable: BE-has-attachment → ItemStack-BlockEntityTag transfer. — DEFERRED with blockPortable per §4.1.**
   This question was load-bearing for whether blockPortable ships in v1. With blockPortable deferred to first-concrete-consumer, Q5 is not a v1 concern. Resolve when blockPortable is reconsidered — at that point, verify Fabric-API hook shape and confirm no vanilla-code-path-ownership mixin is required (Principle 1 gate).

6. **Slot-group bind site vs attachment registration — API clarity.**
   Consumer touches M7 at two points: (a) `StorageAttachment.xxx(...)` at mod init (registration), (b) `.bind(owner)` at menu construction. Is this split clear? Alternative: always bind at registration with a `Supplier<Owner>`, eliminating step (b). Only works for owners with stable identity (`Player` from a server-side context doesn't have stable identity across the session — there's no single `Player` instance).
   **My pull: keep the two-step split.** Registration is about "what is this attachment"; binding is about "which specific owner instance right now." Collapsing them forces the owner to be known at mod init, which doesn't match how menu construction works.

7. **Does M7 force `MenuKitScreenHandler` to own the bindings, or can ad-hoc consumers bind directly? — RESOLVED: two paths.**
   
   **Default path:** MenuKit-owned handler + `StorageAttachment.xxx(...)` factory → handler lifecycle invokes save/load automatically. This is the ergonomic path consumers reach for 95% of the time.
   
   **Decorator path:** Consumer decorating a vanilla menu via `ScreenPanelAdapter` uses `CustomAttachmentSpec` and owns the save/load lifecycle themselves, triggering save on the appropriate vanilla event (the vanilla handler's `removed` hook, or a ScreenEvent equivalent) via their decorator mixin. See §4.4 and §5.3 for the shape.
   
   Two well-documented paths. Consumer picks based on whether they own the menu or decorate it. The library doesn't force them into either — the default is ergonomic, the escape hatch is structural.

8. **Codec for `NonNullList<ItemStack>` — does vanilla already provide?**
   Vanilla has `NonNullList.codec(Codec<E>, int size)` (or thereabouts). Confirm at implementation time. If absent, M7 ships a helper; if present, consumers compose directly.

---

## 11. Non-goals / out of scope

- **Per-player-private slot-group content.** Slot-group content is shared by design (chest's items are the chest's items). Private content is metadata's concern — M1 territory.
- **Multiplayer authority model.** v1 assumes vanilla's menu-opening authority is sufficient. Server decides who opens what menu; M7 trusts whatever writes come through. Authority model can layer on in V2 if public release surfaces pressure.
- **Per-slot-group migration from pre-M7 `Storage` impls.** Consumers migrate manually. Data stored in pre-M7 shapes (IP's `EquipmentData`, V5.7's `EphemeralStorage`) doesn't automatically rehydrate as M7 attachments. IP's equipment data for existing players will need a migration pass in Phase 15a — either one-time reader or deleted on update.
- **Arbitrary consumer types as owners (beyond the modded extension).** v1 ships the four v1 factories + custom specs. Two canonical types (block-portable, entity-attached) are explicitly deferred per §4.1, not "missing" — they adopt M7 additively once concrete consumer evidence surfaces.
- **Content validation or shape policing.** Consumer codec is authoritative. M7 doesn't enforce "this must be a non-empty ItemStack" or similar — if the consumer's codec accepts it, M7 stores it.
- **Cross-server migration.** Same as M1 non-goals — persistence is per-server-instance. Moving a player save between servers carries their attachments; BE/entity attachments stay with the world.
- **Automatic garbage collection.** Block entities destroyed → content lost (structural). No library-level GC. Block-scoped attachments don't accumulate — each BE has its own, and breaking the block clears it.
- **Item-component registration via M7.** M7 wires `itemAttached` storage for a DataComponent the consumer registers through Fabric's standard DataComponentType registration. M7 doesn't register the DataComponent itself — that's consumer work. Library just consumes the registered type.
- **Phase 14b scope lock — v1 ships 4 factories + custom.** `blockPortable` and `entityAttached` deferred per §4.1 Principle 11 per-entry check. Both types can adopt M7 additively once first concrete consumer surfaces — factory registration is small per-entry work, framework already in place.
- **Block-portable (item-form-traveling) persistence.** Not in v1. Shulker-style content-travel is known territory; deferral is "ship on concrete-consumer trigger," not "don't ship."
- **Entity-attached persistence** (donkeys, minecarts-with-chest). Not in v1. Edge-case surface (mount death, dimension crossing, despawn) wants real consumer evidence before commit.

---

## 12. Summary

M7 formalizes where slot-group content persists, by owner type. **Four v1 factories** (`ephemeral`, `playerAttached`, `blockScoped`, `itemAttached` + its `itemContainer` convenience) + a consumer extension (`custom(CustomAttachmentSpec)`) cover vanilla's most common persistence surfaces plus the long tail. `blockScoped` fulfills the vanilla-container-block contract including **drop-on-break**: breaking the block drops the attachment content as item entities before the BE is destroyed. Two additional factories (`blockPortable`, `entityAttached`) defer to first-concrete-consumer trigger per Principle 11's per-entry check — they carry distinct risk beyond "one more factory" and haven't earned the exhaustive-at-v1 exception. Content is `Codec<C>`-serialized, attachment-stored on the natural owner, NBT-native (`/data get`-inspectable), and the library's handler lifecycle invokes save/load at menu close/open.

M7 absorbs Phase 12.5 #7 — `BlockEntityStorage` (Phase-3 TODO stub), `PlayerStorage` (complete-but-unwired), and `ItemStackStorage` (CONTAINER-hardcoded) all delete; their roles are filled by M7 factories. V5.7 Gate B unnarrows because grafted-slot ItemStack now survives disconnect/reconnect. IP's `PlayerAttachedStorage` hand-rolled scaffolding (420 lines across attachments + packets) collapses to ~60 lines declaring two `StorageAttachment.playerAttached(...)` factories.

M7 layers on top of M1's `PersistentContainerKey` + Fabric-attachment infrastructure without unifying. M1 stores metadata (sparse channel-bag per slot). M7 stores content (dense per-slot-group). They share owner resolution; they don't share storage shape. Keep layered.

Consumers pick one of two lifecycle paths: **handler-owned** (MenuKit screen handler invokes save/load automatically — the default) or **decorator-path** (consumer uses `CustomAttachmentSpec` and owns save/load from their decoration mixin — the escape hatch). Both are documented and supported.

**Status: ready for advisor round 3.** Seven open questions in §10 are now resolved (six by implementer, one deferred with blockPortable per Q5). Round 2 pushbacks on scope trim, two-lifecycle-paths, and doc-consistency findings all incorporated. Implementation begins once round 3 closes.
