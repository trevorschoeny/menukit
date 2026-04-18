# M1 — Unified Per-Slot Persistent State

**Phase 12 mechanism — persistence-shaped** (per `Phase 11/POST_PHASE_11.md`).

**Status: Resolved — ready for implementation.**

**Enables:** F1 (persistent player-slot lock across sessions), F2 (chest-slot lock visibility across menu reopens), future per-slot metadata in any consumer mod.

**Companion primitive:** `SlotIdentity` (M2, already shipped). M1 builds on M2 — session identity is free; persistence across sessions is the new capability.

---

## 1. Purpose

Consumer mods need per-slot state that survives beyond the current menu, session, and sometimes game run. IP's sort-lock is the canonical example: a player locks slot 15 in their main inventory, then logs out. On next login, slot 15 should still be locked. Place a chest, lock slot 4, walk away; come back and reopen — slot 4's lock should be visible without the player re-locking it.

Current Phase-11 shape is in-memory only. `ClientLockStateHolder` / `ServerLockStateHolder` use `WeakHashMap<Container, Map<Integer, SlotLockState>>`. The keys die with the JVM — disconnect drops all state; chunk-unload + reload produces new `BlockEntity` / `Container` references and the locks vanish. `SortLockC2SPayload` syncs toggles on the wire, but there is no sync-on-menu-open, so even mid-session a player who reconnects sees an empty lock overlay until they re-toggle.

M1 fills two gaps:

- **Persistence.** State survives disconnect / chunk unload / game restart, scoped to the natural owner of the slot (player UUID, block position + dimension, entity UUID).
- **Sync on menu open.** Server pushes known per-slot state to the opening client so the lock overlay is correct immediately. Toggles round-trip through the server (unchanged from today's pattern).

M1 is additive: the session-scoped `SlotIdentity` primitive stays as-is. Consumers that don't need persistence (in-flight mutation tracking, UI-only state) continue keying their maps on `SlotIdentity` directly. M1 is for state that should survive.

---

## 2. Consumer evidence

### IP — sort-lock (F1 + F2)

- Each slot carries a boolean `sortLocked` flag. Set via lock keybind on hovered slot.
- Read by: sort algorithm (skip locked), move-matching (skip locked source + destination), lock overlay render.
- Today: session-scoped, per-session. After Phase 11, F1 (player inventory persistence) + F2 (chest visibility across reopens) deferred to M1.

### IP — potential future full-lock (F3)

- A broader lock that blocks direct PICKUP clicks, not just auto-routing. Different semantic from sort-lock.
- Suggests **typed channels** — a consumer declares multiple named channels (one per state variety) rather than a single catch-all bag. Sort-lock and full-lock coexist without mutual interference.

### Shulker-palette — ruled out

Phase 11 audit confirmed shulker-palette's per-item palette flag is self-contained via `CUSTOM_DATA` on the ItemStack. **Per-item state is explicitly NOT an M1 concern** — the item carries its own state, travels with it through menus, storage, and worlds. M1 is strictly per-slot.

### Other mods — speculative

- Inventory annotations (per-slot labels)
- Per-slot filter predicates
- Multiplayer shared lock state (V2+ — v1 is per-player private)

---

## 3. Scope

- **Per-slot state keyed by a stable identity per container type** (player UUID for inventory, block position + dimension for block entities, entity UUID for entity-backed, player UUID for ender chest).
- **Server-authoritative.** Client writes are advisory; server validates, stores, and broadcasts back.
- **Per-player private.** The state a player sets is visible only to that player. V1 does not support shared lock state where multiple players see each other's marks on a shared chest.
- **Typed channels.** Each consumer-declared channel has a codec, a default value, and its own namespace. Channels are additive and independent.
- **Vanilla container types only in v1.** Player inventory, the canonical block-entity-backed containers (chest, shulker box, barrel, hopper, dispenser, dropper, furnace family), the ender chest, and entity-backed containers (donkey / mule / llama / minecart-with-chest / minecart-with-hopper). Modded containers fall through to a documented extension point — not an automatic fit.

---

## 4. Design decisions

Each decision is a **draft position** open to advisor feedback. The shape of §4.2 and §4.4 is load-bearing for the rest of the doc.

### 4.1 Identity strategy — persistent key per container type

`SlotIdentity` gives session-scoped `(Container, index)` identity. For persistence, M1 resolves a `Container` to a `PersistentContainerKey` tagged with the container's natural owner:

```java
public sealed interface PersistentContainerKey {
    record PlayerInventory(UUID playerId) implements PersistentContainerKey {}
    record EnderChest(UUID playerId) implements PersistentContainerKey {}
    record BlockEntityKey(BlockPos pos, ResourceKey<Level> dimension) implements PersistentContainerKey {}
    record EntityKey(UUID entityId) implements PersistentContainerKey {}
    record Modded(Identifier resolverId, CompoundTag payload) implements PersistentContainerKey {}
}
```

The library provides a `resolvePersistentKey(Container)` function that switches on known vanilla types. Each branch returns the matching key. Unknown types return `Optional.empty` — those containers don't support per-slot persistence in v1.

**Resolution table** — vanilla types, all present in 1.21.11:

| Container type | Key |
|----------------|-----|
| `Inventory` (Player's) | `PlayerInventory(player.getUUID())` |
| `PlayerEnderChestContainer` | `EnderChest(player.getUUID())` |
| `RandomizableContainerBlockEntity` (chest, shulker box, barrel, trapped chest) | `BlockEntityKey(pos, dim)` |
| `HopperBlockEntity` | `BlockEntityKey(pos, dim)` |
| `DispenserBlockEntity` / `DropperBlockEntity` | `BlockEntityKey(pos, dim)` |
| `AbstractFurnaceBlockEntity` (furnace, smoker, blast furnace) | `BlockEntityKey(pos, dim)` |
| `BrewingStandBlockEntity` | `BlockEntityKey(pos, dim)` |
| Horse/donkey/llama/mule storage (`AbstractHorse`/etc.) | `EntityKey(entity.getUUID())` |
| `AbstractMinecartContainer` | `EntityKey(entity.getUUID())` |
| `CraftingContainer` (crafting grid, anvil, grindstone, etc.) | — (ephemeral; returns empty) |

#### By-reference-to-owner composition

State in M1 is **by-reference to its owner**: the player UUID, block-entity position + dimension, or entity UUID *identifies* the state's home, and the owner's Fabric attachment holds it. Lifecycle alignment follows naturally — the owner dies, the state dies. Block broken → BE's attachment gone. Entity killed → entity's attachment gone. Player removed from the save → player's attachment gone. No library-level GC, no reference-counting, no WeakReference tracking. Ownership is structural, cleanup is structural.

This matches the M5 §4A framing from a different angle. M5 distinguished by-value (flows in layout) from by-reference (fixed coord that others depend on). M1 is a third kind: by-reference-to-owner (state whose home is determined by the structural owner of the slot, and whose lifetime is the owner's). The three patterns coexist without conflict because each answers a different question — where does the panel render, where does the backdrop go, and where does per-slot state live.

The `Modded(Identifier, CompoundTag)` case applies **library-not-platform** correctly: the library hands off key shape to the consumer (mod defines the tag structure), stores opaquely (library never parses the payload), and doesn't try to understand modded storage semantics. The library's responsibility is to thread the payload through the attachment read/write path; the mod's responsibility is to define what the payload means.

#### Creative mode — source finding

**Both `InventoryMenu` and `CreativeModeInventoryScreen.ItemPickerMenu` construct their player-inventory slots with `player.getInventory()` as the `Container` reference.** Verified against 1.21.11 vanilla source:

- `InventoryMenu.<init>(Inventory inventory, …)` calls `addStandardInventorySlots(inventory, 8, 84)` plus armor + offhand slots, all bound to the passed `Inventory`.
- `ItemPickerMenu.<init>(Player player)` calls `Inventory inventory = player.getInventory();` then `addInventoryHotbarSlots(inventory, 9, 112)`. The creative-tab grid uses a separate static `CONTAINER` field (the scrollable picker contents), but the bottom-row inventory + hotbar slots reference the same `Inventory` instance as `InventoryMenu`.

**Consequence:** `resolvePersistentKey` returns the same `PlayerInventory(playerUUID)` key regardless of which menu the slot was observed from. State set via the creative inventory screen is visible when the player switches to survival and opens the survival inventory — automatically, no canonicalSlot mapping or cross-menu routing. M1 inherits this for free from vanilla's container-sharing.

The creative tab grid's picker slots (backed by the static `CreativeModeInventoryScreen.CONTAINER`) fall through to the "unknown type" branch and return `Optional.empty` — per-slot state on those isn't meaningful anyway (they're a paginated view of the item registry, not persistent storage).

### 4.2 Persistence — Tag-native storage via Fabric attachments

**NBT throughout, per THESIS.md principle 6.** State stored by M1 uses `Tag` (vanilla's NBT type tree) as the in-memory shape, `Codec<T>` for value serialization, and Fabric attachments for transport. Every piece of persisted state is inspectable via `/data get` on the owner. The library stashes nothing opaque in an otherwise legible NBT subtree.

State lives in Fabric attachments on the natural owner of each key variant:

| Key variant | Attachment owner | Attachment scope |
|-------------|------------------|-------------------|
| `PlayerInventory` | `Player` | Per-player, persisted in player data |
| `EnderChest` | `Player` (separate attachment from PlayerInventory) | Per-player |
| `BlockEntityKey` | `BlockEntity` | Serialized with the BE's NBT; dies with the block |
| `EntityKey` | `Entity` | Serialized with the entity's NBT; dies with the entity |
| `Modded` | Stored via the modded resolver's registered binding on its mod-owned owner type | Consumer-defined |

**One library-owned attachment per owner type, holding all channel state in a typed bag.** The alternative is one attachment per channel — that means adding a new channel requires registering a new attachment type per owner. The bag approach is simpler and keeps library attachment registration stable.

**Attachment payload shape** (Tag-native, not `byte[]`):

```java
public record SlotStateBag(Map<Identifier, Map<Integer, Tag>> channels) {
    // channels.get(channelId).get(containerSlotIndex) = codec-encoded Tag value
}
```

Encode/decode path:

```java
// Write
Tag encoded = channel.codec().encodeStart(NbtOps.INSTANCE, value)
                     .getOrThrow();
bag.channels()
   .computeIfAbsent(channel.id(), k -> new HashMap<>())
   .put(containerSlotIndex, encoded);

// Read
Tag encoded = bag.channels()
                 .getOrDefault(channel.id(), Map.of())
                 .get(containerSlotIndex);
if (encoded == null) return channel.defaultValue();
return channel.codec().parse(NbtOps.INSTANCE, encoded)
              .getOrThrow(err -> new IllegalStateException("Codec mismatch for " + channel.id()));
```

No double-encoding through `Codec → NBT → byte[] → ByteArrayTag`. The library stores values as their native `Tag` form. Unknown-channel preservation (a channel registered by a mod that isn't loaded this session) still works: a `Tag` sits in the map, unparsed, until someone registers the matching channel id and reads it. The `Tag` is opaque to the library without being opaque to vanilla tooling — `/data get` sees it, NBT editors parse it.

**Chunk unload / reload.** Block-entity attachments serialize with the BE's NBT. On chunk reload the BE reconstructs, Fabric rehydrates the attachment, and the state comes back intact. No library work required.

**Block break.** BE destroyed → attachment destroyed. State lost. This is correct: the block is gone. (Items the block drops don't inherit slot state — if that's ever wanted, it becomes a per-item concern via `CUSTOM_DATA`, which is out of scope for M1.)

**Entity death.** Same story — entity attachment goes with the entity.

**Cross-world mobility.** Minecarts can cross dimensions; donkeys generally can't. Entity attachments move with the entity. Block entities don't move (breaking + placing is destroy + create, which resets state). This is the intended semantic.

#### Storage-shape tradeoff — BE-hosted `Map<UUID, Bag>` for future shared-state migration

For block-entity-backed containers, per-player private state could live in two places. Both have real costs; the decision is shaped by where we're headed, not just where we are.

**Alternative considered — player-hosted:**

```java
// On the player attachment
Map<BlockEntityKey, SlotStateBag> perBE;
```

Per-player data travels with the player. BE NBT stays clean. The catch: V2 shared-state lock visibility (Player A locks slot 4, Player B opens the same chest, Player B sees it locked) requires data migration from every active player's attachment into the BE. That's a loud, messy, runtime migration — every player-hosted entry has to be gathered and merged into the BE the first time V2 applies.

**Chosen — BE-hosted:**

```java
// On the block-entity attachment
Map<UUID, SlotStateBag> perPlayer;
```

BE NBT grows with every player who ever opened it. In return, V2 shared-state is a single-step migration: drop the UUID layer, merge bags by preferred policy (union? latest-wins? per-channel?), done. The storage shape aligns with where V2 is going.

**Why this wins.** V2 shared-state is plausible — it's a frequent community request for multiplayer servers — and the migration shape should be designed in, not retrofitted. The BE NBT growth is bounded (per-BE, not per-world), and breaking + replacing the BE cleans it. For Trevor's single-player case the overhead is one player × BEs-ever-opened; negligible.

**V1 consequence — no per-player GC on BE attachments.** Entries accumulate; there's no TTL, no cleanup pass. V2 may introduce policy-based pruning if public release surfaces pressure. Flagged as a non-goal in §11.

**V2 migration hook — channel visibility.** V2 will add a `Visibility` parameter on channel registration (`PRIVATE` / `SHARED`). V1 is PRIVATE-hardcoded. When V2 lands, default stays PRIVATE; SHARED is opt-in per-channel. The `Map<UUID, Bag>` storage shape supports this migration without rewriting storage — a SHARED channel's values get promoted out of the per-player map into the BE-level map at V2-upgrade time.

### 4.3 Sync — server authoritative, client session cache

**Writes are server-authoritative.** A client wanting to change state sends `SlotStateUpdateC2SPayload(menuSlotIndex, channelId, encodedValue)`. The server:

1. Resolves the slot via `Slot slot = player.containerMenu.slots.get(menuSlotIndex);`.
2. Extracts `int containerSlotIndex = slot.getContainerSlot();` — the container-relative index, stable across menus.
3. Resolves the slot's `PersistentContainerKey` from `slot.container`.
4. Authorization check: v1 is per-player private, so any write to a slot the player's current menu exposes is authorized.
5. Reads the current value from persistent storage (indexed by `containerSlotIndex`), applies the new value, writes back.
6. Broadcasts `SlotStateUpdateS2CPayload(persistentKey, channelId, containerSlotIndex, encodedValue)` to every client observing the container. V1: just the writing player. V2+: multiple observers.

**Client receives `SlotStateUpdateS2CPayload`** → resolves the persistent key against the currently-open menu's slots (scanning for a `Slot` whose `getContainerSlot()` matches the incoming `containerSlotIndex` and whose `container` resolves to the matching persistent key) → updates the client-side session cache keyed by `SlotIdentity.of(slot)`. If no matching slot is in the current menu, update is silently discarded (client will re-sync on next open).

**Slot-index discipline.** Two indices are at play:

- **`menuSlotIndex`** = `slot.index` — the slot's position within the current menu's `slots` list. Changes between menus (slot 36 of `InventoryMenu` = slot 36 of the hotbar; slot 36 of a `ChestMenu` = first row of player inventory). The client's natural addressing (hover coords map to menu indices).
- **`containerSlotIndex`** = `slot.getContainerSlot()` — the slot's position within its backing `Container`. Stable across every menu that exposes the same container. This is what persistent storage keys on.

Wire protocol carries `menuSlotIndex` for writes (client doesn't know the container-relative index without resolving the slot) and `containerSlotIndex` for broadcasts (server has already resolved, client looks up the slot by scanning). The server translates at the packet-handler boundary; storage always keys on `containerSlotIndex`.

#### Menu-open snapshot flow

When the server constructs a menu for a player (chest, furnace, donkey, peek session, etc.), after `addSlot` calls complete but before the `OpenScreen` packet ships, the library iterates `menu.slots` and bundles the relevant state:

1. For each slot, resolve `PersistentContainerKey` from `slot.container`. Skip if empty (unsupported container type).
2. Extract `containerSlotIndex = slot.getContainerSlot()`.
3. For each registered channel, read the value from persistent storage. Skip if equal to the channel's default.
4. Pack into `SlotStateSnapshotS2CPayload(menuId, entries)` where each entry is `(menuSlotIndex, channelId, encodedTag)`. (Wire uses `menuSlotIndex` because the client addresses by menu position; server already did the translation.)
5. Send the snapshot to the opening player just after the open-screen packet.

Hook: mixin on `ServerPlayer.openMenu` (or equivalent), post-open. Covers block-entity-backed menus (chest, shulker box, furnace, hopper, etc.), entity-backed menus (donkey, minecart), and any custom menu that routes through `openMenu`.

#### Player-join snapshot flow — peer path for player-scoped channels

**The menu-open hook does not cover the player's own inventory.** `InventoryMenu` is constructed in the `Player` constructor, assigned directly to `containerMenu`, and never routes through `openMenu`. Without a second hook, F1 breaks: the player locks slot 15, disconnects, reconnects, presses E — client's session cache is empty, lock overlay shows nothing, player thinks state was lost.

M1 adds a peer snapshot path for player-scoped channels:

1. **On player join** — fires via `ServerPlayConnectionEvents.JOIN` (Fabric API).
2. **On respawn post-death** — respawn reconstructs `InventoryMenu`; same delivery needed. Fires via a mixin on `PlayerList.respawn` (or `ServerPlayer.teleport` for end-portal exits).

Both events converge on the same handler:

1. For each slot in `player.inventoryMenu.slots`, resolve `PersistentContainerKey`.
2. Filter: only `PlayerInventory(player.getUUID())` and `EnderChest(player.getUUID())` variants apply here. Other keys (BlockEntity, Entity, Modded) are the menu-open path's concern.
3. Build the snapshot the same way as the menu-open flow, targeted at `player.inventoryMenu`.
4. Send `SlotStateSnapshotS2CPayload` to the joining/respawning player.

The same packet type carries both paths. The client doesn't need to distinguish — it just receives a snapshot with a `menuId` matching the current menu and populates its cache accordingly.

**Why a peer path and not a single unified hook.** Menu-open fires per container-interaction; player-join fires once per session. Different trigger points; parallel handling. Trying to unify them into "any event that opens a menu" means chasing every vanilla code path that assigns to `containerMenu` — a moving target. Two explicit hooks is stable.

#### Client session cache

When the client receives any snapshot (menu-open or player-join), it populates a session cache keyed by `SlotIdentity`. The cache structure:

```java
// Client-only, session-scoped
WeakHashMap<Container, Map<Integer, Map<Identifier, Object>>> cache;
//                    ↑              ↑           ↑
//                    Container   containerSlotIndex   channelId → decoded value
```

Reads for the duration of the menu go against the cache, not storage. When the menu closes, the `Container` references die naturally (for BE-backed menus — the client's session `Container` is an ephemeral wrapper); the next open triggers another snapshot. For player-scoped containers (`Inventory`, `PlayerEnderChestContainer`), the references persist for the player's session, so the cache stays populated across menu opens.

**Why session cache instead of querying live.** The client doesn't have Fabric attachments for BEs outside its loaded chunks — it has no copy of the BE NBT until the server sends it. The snapshot packet is the delivery mechanism. Once received, session cache + mutation syncs keep client and server in lockstep until menu close.

### 4.4 API shape — typed channels, static register-and-use

```java
// Registration at mod init
public static final SlotStateChannel<Boolean> SORT_LOCK = MKSlotState.register(
        Identifier.of("inventory-plus", "sort_lock"),
        Codec.BOOL,
        ByteBufCodecs.BOOL,
        false);

// Read (client or server — returns the same value)
boolean locked = SORT_LOCK.get(slot);

// Write (any side — library handles the wire protocol)
SORT_LOCK.set(slot, true);
```

The `SlotStateChannel<T>` record carries the channel's identifier, codec, stream codec, and default value. The `.get` / `.set` methods dispatch internally to either the client session cache or the server persistent storage depending on the execution side.

**Side detection.** The library checks whether the calling thread is the client or server thread (standard Fabric pattern — `instanceof ServerPlayer` on the player associated with the call) and dispatches. Consumers don't have to think about which side they're on.

**Slot-less overload (for pre-menu-open initialization or cross-menu queries).**

```java
SORT_LOCK.get(persistentKey, containerSlotIndex);  // read from persistent storage directly
SORT_LOCK.set(persistentKey, containerSlotIndex, true);  // server-side only
```

Primary API uses `Slot`. Slot-less API is the escape hatch for server-side tooling.

### 4.5 Non-authorization in v1 — per-player private

V1 does not implement cross-player authorization beyond "the player's current menu exposes this slot." Shared chest lock visibility (Player A locks slot 4; Player B opens the same chest and sees it locked) is out of scope — see §11 non-goals.

Consequences:
- `SlotStateBag` on a block entity is **per-player-per-block-entity**. Each player who opens a chest has their own set of lock marks on that chest's slots. Physical inventory is shared; lock state is private.
- The BE attachment holds `Map<PlayerUUID, SlotStateBag>` — a top-level map by player UUID.
- Chunk unload serializes all players' marks on that BE. Rarely-used-but-still-tracked player marks persist. Acceptable overhead for v1.

**Alternative considered and deferred to V2+:** shared lock state across players. Requires an authority model (who can unlock what?), conflict resolution, and a visibility UX (whose lock shows? everyone's?). Out of scope for v1 — the only current consumer (IP sort-lock) is already private-per-player.

### 4.6 Channel registration — first-registration-wins

Channels register at mod init via `MKSlotState.register(...)`. First registration of a given identifier wins; subsequent registrations of the same id log a warning and return the already-registered channel. The warning makes accidental collisions visible without blowing up the session.

**Why not "idempotent on parameter equality."** The draft considered requiring duplicate registrations to have matching `Codec`, `StreamCodec`, and default. The problem: value equality on `Codec` instances isn't reliable — the JDK's `Codec.BOOL` and `ByteBufCodecs.BOOL` compare by reference, and even structurally-identical codecs constructed by different mods aren't `.equals`. A strict-equality check would reject compatible re-registrations and force a workaround convention. First-wins sidesteps this: if mod A and mod B both try to register `"my-family:sort_lock"`, the first one sticks; the second gets a warning; consumers that want to read the channel call `MKSlotState.getChannel(id)` to retrieve the live channel (with whatever codec the first registrant chose).

**Discovery.** No ecosystem-wide channel registry. Consumer mods know what channels they care about; the library doesn't enumerate. If mod A wants to read mod B's channel, A looks it up by identifier via `MKSlotState.getChannel(id)` rather than re-registering.

**Unregistered lookup.** Calling `MKSlotState.getChannel(id)` for an unregistered id returns `Optional.empty`. Calling `someChannel.get(slot)` where `someChannel` was never registered is impossible — a consumer can't construct a `SlotStateChannel` without going through `register`. (See §10 decision 1 for the semantics if someone calls `.get` on a channel after its mod unloads mid-session: the cached channel instance still reads its in-bag Tag; the library doesn't gate on "still-registered.")

Channels are global across the JVM — both client and server register the same channels at mod init so codec and default value agree. Wire protocol uses the channel id `Identifier` as the key.

### 4.7 Modded container — explicit registration, not auto-detect

For a mod's custom container to participate in M1:

```java
MKSlotState.registerContainerResolver(MyModChest.class, be -> {
    CompoundTag payload = new CompoundTag();
    payload.putLong("pos", be.getBlockPos().asLong());
    payload.putString("dim", be.getLevel().dimension().location().toString());
    return new PersistentContainerKey.Modded(
            Identifier.of("my-mod", "custom_chest"),
            payload);
});

// And register an attachment binding so the library knows where to store state:
MKSlotState.registerModdedAttachmentBinding(
        Identifier.of("my-mod", "custom_chest"),
        MyModChest.class);  // owner class for the library's SlotStateBag attachment
```

The payload is a `CompoundTag` — the mod owns the payload shape; the library stores it opaquely. No serialization format is imposed beyond "valid NBT." This applies library-not-platform correctly: the library hands off key shape to the consumer, stores as `Tag` (legible to `/data get`), and doesn't try to understand modded storage semantics.

Mod is responsible for making its BE / entity class Fabric-attachment-capable (which Fabric supports for `BlockEntity` and `Entity` subclasses). The library reads/writes the attachment transparently once the resolver + binding are registered.

**Fallback when no resolver is registered.** If a mod's container type appears in a menu and no resolver is registered, per-slot state for that container doesn't persist — the library falls back to the session-cache-only path, matching today's `ClientLockStateHolder` behavior. The sort-lock keybind still works (visible for the session) but doesn't survive disconnect. Consumers that want persistence on modded containers explicitly opt in.

---

## 5. Consumer API — before / after

### 5.1 IP sort-lock (F1 migration)

**Before** (eight files, multiple paths):

```java
// ClientLockStateHolder.java — 82 lines, WeakHashMap + SlotIdentity keying
public static boolean isSortLocked(Slot slot) { ... }
public static SlotLockState getOrCreate(Slot slot) { ... }

// ServerLockStateHolder.java — 85 lines, per-player UUID + Container map
public static boolean isSortLocked(ServerPlayer player, Slot slot) { ... }
public static void setSortLocked(ServerPlayer player, Slot slot, boolean v) { ... }

// SortLockC2SPayload.java — 34 lines, toggle packet
// SlotLockState.java — 25 lines, { boolean sortLocked }
// KeybindDispatch.handleLock — toggles client, sends packet
// InventoryPlus.java — registers server-side receiver
```

**After:**

```java
// inventory-plus/locks/IPSlotState.java
public final class IPSlotState {
    public static final SlotStateChannel<Boolean> SORT_LOCK = MKSlotState.register(
            Identifier.of("inventory-plus", "sort_lock"),
            Codec.BOOL, ByteBufCodecs.BOOL, false);
    private IPSlotState() {}
}

// Keybind toggle becomes:
boolean locked = IPSlotState.SORT_LOCK.get(slot);
IPSlotState.SORT_LOCK.set(slot, !locked);
```

That's the full before/after. `ClientLockStateHolder`, `ServerLockStateHolder`, `SortLockC2SPayload`, `SlotLockState`, and the `SortLockC2SPayload` receiver all delete. Lock-overlay render goes from `ClientLockStateHolder.isSortLocked(slot)` to `IPSlotState.SORT_LOCK.get(slot)`. Sort-algorithm and move-matching scoping go through the same accessor.

**Persistence falls out automatically.** F1 (player-slot lock across sessions) and F2 (chest-slot lock visibility across reopens) are both enabled by the new primitive without additional IP-side code.

### 5.2 IP full-lock (F3, future)

```java
public static final SlotStateChannel<Boolean> FULL_LOCK = MKSlotState.register(
        Identifier.of("inventory-plus", "full_lock"),
        Codec.BOOL, ByteBufCodecs.BOOL, false);
```

Separate channel, separate semantics. Sort-lock and full-lock coexist on the same slot without interfering. Read sites that care about "any lock blocks this" check both:

```java
if (SORT_LOCK.get(slot) || FULL_LOCK.get(slot)) { skip }
```

### 5.3 Hypothetical consumer — per-slot annotations

```java
public static final SlotStateChannel<String> ANNOTATION = MKSlotState.register(
        Identifier.of("annotator-mod", "label"),
        Codec.STRING, ByteBufCodecs.STRING_UTF8, "");
```

Arbitrary typed state via codec. Codec-serializable = M1-persistable.

---

## 6. Sync mechanics (detail)

### 6.1 Registration

Library registers three packet types at client + server init:

- `SlotStateSnapshotS2CPayload(int menuId, List<SnapshotEntry> entries)` — bulk snapshot delivery (menu-open + player-join paths share this type)
- `SlotStateUpdateS2CPayload(Identifier channelId, int containerSlotIndex, Tag encodedValue)` — per-mutation broadcast, indexed by container-relative slot
- `SlotStateUpdateC2SPayload(Identifier channelId, int menuSlotIndex, Tag encodedValue)` — client-initiated write, indexed by menu-relative slot (server translates)

Channels register via `MKSlotState.register(...)`. Container resolvers via `MKSlotState.registerContainerResolver(...)`. All at mod init.

**Vanilla-client behavior.** Fabric's networking layer only sends payloads to clients that have registered handlers for them. A vanilla (non-MenuKit) client connecting to a MenuKit-aware server receives no snapshots or update broadcasts — the server still stores state locally (writes never arrive because the vanilla client has no way to send them). State persists server-side; it's just invisible to the vanilla client. This is the correct degradation path.

### 6.2 Menu-open snapshot flow (server)

```
Server: player.openMenu(...)
         ↓ vanilla opens the menu, sends OPEN_SCREEN packet
         ↓ library hook (mixin on Player.openMenu or equivalent, post-open)
         ↓ iterate menu.slots
         ↓ for each slot at menuSlotIndex:
             key = resolvePersistentKey(slot.container)
             if key.isEmpty() skip
             containerSlotIndex = slot.getContainerSlot()
             for each registered channel:
                 Tag encoded = readFromAttachment(key, channel.id(), containerSlotIndex)
                 if encoded == null skip  (no value stored)
                 add (menuSlotIndex, channelId, encoded) to snapshot
         ↓ if snapshot non-empty:
             send SlotStateSnapshotS2CPayload(menuId, entries)
```

Empty snapshot (no slots have non-default state) sends nothing — the client's session cache starts empty, and reads return the channel default.

Wire carries `menuSlotIndex` so the client can address slots by the position it already knows; the server did the translation to `containerSlotIndex` for its storage lookup.

### 6.2a Player-join snapshot flow (server) — peer path for player-scoped channels

```
Server: ServerPlayConnectionEvents.JOIN fires
        OR: mixin on PlayerList.respawn post-construction
         ↓ library handler runs on the player's InventoryMenu
         ↓ iterate player.inventoryMenu.slots
         ↓ for each slot at menuSlotIndex:
             key = resolvePersistentKey(slot.container)
             if key not PlayerInventory or EnderChest variant: skip
             (other variants are the menu-open path's concern)
             containerSlotIndex = slot.getContainerSlot()
             for each registered channel:
                 Tag encoded = readFromAttachment(key, channel.id(), containerSlotIndex)
                 if encoded == null skip
                 add (menuSlotIndex, channelId, encoded) to snapshot
         ↓ send SlotStateSnapshotS2CPayload(inventoryMenuId, entries)
```

Same packet type as §6.2. The client handles both paths identically — on receipt, look up `menu.slots.get(menuSlotIndex)` in the currently-open menu and populate the cache.

**Why a peer path.** `InventoryMenu` is constructed in the `Player` constructor and assigned to `containerMenu` directly, never going through `openMenu`. The "open inventory" keybind (E) is purely client-side — no server event fires when the player presses E. Without this peer path, F1 breaks: a player locks slot 15, disconnects, reconnects, presses E, and the overlay shows nothing because the server never sent the snapshot.

### 6.3 Snapshot receive (client)

```
Client: receives SlotStateSnapshotS2CPayload(menuId, entries)
         ↓ menuId matches currently-open menu?
         ↓ if no — discard (menu closed between open and snapshot — rare race;
                          also fires for player-join before E is pressed — that's fine,
                          inventory snapshot lands when player.inventoryMenu is live)
         ↓ if yes:
             for each entry (menuSlotIndex, channelId, encodedTag):
                 Slot slot = menu.slots.get(menuSlotIndex)
                 channel = MKSlotState.getChannel(channelId)
                 if channel is empty: cache raw Tag (opaque preserve)
                 else: value = channel.codec().parse(NbtOps.INSTANCE, encodedTag)
                       sessionCache.put(SlotIdentity.of(slot), channelId, value)
```

The cache is keyed by `SlotIdentity.of(slot)` which internally uses `slot.container` + `slot.getContainerSlot()` — the container-relative coordinate that stays stable across menu transitions for shared-container cases (player inventory visible in chest screens, etc.).

### 6.4 Client write flow

```
Client: SORT_LOCK.set(slot, true)
         ↓ library detects client side
         ↓ menuSlotIndex = slot.index   (the slot's position in current menu)
         ↓ update session cache immediately (optimistic, keyed by SlotIdentity.of(slot))
         ↓ encode value via channel.codec() to a Tag
         ↓ send SlotStateUpdateC2SPayload(SORT_LOCK.id(), menuSlotIndex, encodedTag)
         ↓ server receives
             Slot slot = player.containerMenu.slots.get(menuSlotIndex)
             PersistentContainerKey key = resolvePersistentKey(slot.container)
             if key empty: drop (unsupported container)
             int containerSlotIndex = slot.getContainerSlot()
             writeToAttachment(key, SORT_LOCK.id(), containerSlotIndex, encodedTag)
             broadcast SlotStateUpdateS2CPayload(SORT_LOCK.id(), containerSlotIndex, encodedTag)
         ↓ client receives broadcast
             resolve current menu's slot whose getContainerSlot() == containerSlotIndex
                    and whose container resolves to the matching key
             update session cache (idempotent — value already there optimistically)
```

**Optimism note.** The client updates its cache before the server ACKs. If the server rejects the write (authorization fails — v1 has no rejection), the subsequent broadcast contains the server-authoritative value, and the client's optimistic state gets overwritten. Since v1 has no rejection scenarios, this is effectively always a no-op reconciliation.

**Why broadcasts carry `containerSlotIndex` not `menuSlotIndex`.** A mutation made while Player A has the chest open also needs to land in Player B's cache if Player B opens the chest later — so the update is addressed in container-relative terms (stable across observers). For V1 the broadcast targets only the writing player, but the addressing shape is V2-ready.

### 6.5 Server write flow (server-initiated, e.g., tooling)

```
Server-side caller: SORT_LOCK.set(slot, true)    // slot from some server-side menu or construction
         ↓ library detects server side
         ↓ PersistentContainerKey key = resolvePersistentKey(slot.container)
         ↓ int containerSlotIndex = slot.getContainerSlot()
         ↓ encode value to a Tag via channel.codec()
         ↓ writeToAttachment(key, channel.id(), containerSlotIndex, encodedTag)
         ↓ broadcast SlotStateUpdateS2CPayload(channel.id(), containerSlotIndex, encodedTag)
           to every client observing the container
         ↓ (v1 = just the owning player; V2+ = all observers)
```

The slot-less server overload (`channel.set(persistentKey, containerSlotIndex, value)`) is identical from step 3 onward. Consumers calling the slot overload from server code save themselves the key resolution; the library does it for them.

### 6.6 Cleanup semantics

| Event | Effect |
|-------|--------|
| Client disconnect | Client session cache cleared (process exit — natural GC) |
| Server-side player disconnect | Player attachment persists (Fabric serializes on player save) |
| Chunk unload | BE attachment serializes to chunk NBT |
| Block break | BE destroyed → attachment destroyed. State lost. |
| Entity death | Entity attachment destroyed. State lost. |
| Mod uninstall | Channel id no longer registered; existing `Tag` payloads in attachments remain opaque — library doesn't parse unknown channels, just preserves them. Re-installing the mod re-registers the channel, and reads work again. |

---

## 7. Library surface

**New files:**

- `core/MKSlotState.java` — public entry point: `register`, `getChannel`, `registerContainerResolver`, `registerModdedAttachmentBinding`. The consumer's main touchpoint.
- `core/SlotStateChannel.java` — typed channel record with `.get(slot)` / `.set(slot, v)` / slot-less variants. Holds `Codec<T>` (persistence, Tag-bound) + `StreamCodec<RegistryFriendlyByteBuf, T>` (wire) + default value.
- `core/PersistentContainerKey.java` — sealed interface + five concrete records (PlayerInventory, EnderChest, BlockEntityKey, EntityKey, Modded). Modded payload is `CompoundTag`.
- `state/SlotStateBag.java` — internal attachment payload type: `Map<Identifier, Map<Integer, Tag>>`. Ships with a Codec for Fabric persistence + a StreamCodec for the snapshot packet. Not public — consumers never touch bags directly.
- `state/SlotStateRegistry.java` — internal: channel list keyed by `Identifier`, container-resolver map, modded-binding map.
- `state/SlotStateClientCache.java` — internal session cache (`WeakHashMap<Container, Map<Integer, Map<Identifier, Object>>>`), mirrors the ClientLockStateHolder structure.
- `state/SlotStateServer.java` — internal server-side persistence facade. Resolves a `PersistentContainerKey` to the correct attachment owner (Player / BlockEntity / Entity) and reads/writes the `SlotStateBag`.
- `state/SlotStateAttachments.java` — internal: registers the four library-owned attachments (Player for PlayerInventory, Player for EnderChest (separate), BlockEntity generic, Entity generic). Each uses Codec-based persistence via NbtOps.
- `network/SlotStateSnapshotS2CPayload.java` — bulk snapshot delivery (shared by menu-open + player-join paths).
- `network/SlotStateUpdateS2CPayload.java` — mutation broadcast, carries `containerSlotIndex` + `Tag`.
- `network/SlotStateUpdateC2SPayload.java` — client-initiated write, carries `menuSlotIndex` + `Tag`.
- `mixin/PlayerOpenMenuMixin.java` — server-side hook on `Player.openMenu` (or `ServerPlayer.openMenu` — mixin selected at implementation): after open, library sends the menu-open snapshot.
- `hook/SlotStatePlayerJoinHandler.java` — registers a `ServerPlayConnectionEvents.JOIN` callback in `MenuKit.initServer`; dispatches the player-join snapshot for `player.inventoryMenu`.
- `mixin/PlayerListRespawnMixin.java` — server-side hook on `PlayerList.respawn` (post-respawn-construction): fires the same handler as player-join so the respawned player's inventory snapshot lands.

**Modified files:**

- `MenuKit.java` — initialize `SlotStateRegistry` + packet registration + player-join handler in `initServer` / `initClient`.
- `menukit.mixins.json` — register `PlayerOpenMenuMixin` and `PlayerListRespawnMixin`.

**Unchanged:**

- `SlotIdentity` — stays as the session primitive. M1's cache + runtime APIs use it internally; the persistent key is a separate concept layered above.

---

## 8. Migration plan — Phase 13 consumer work

**13e-1: IP sort-lock migration.** When M1 ships:

1. Add `IPSlotState` channel registration.
2. Replace `ClientLockStateHolder.isSortLocked(slot)` with `IPSlotState.SORT_LOCK.get(slot)` at every call site (sort algorithm, move-matching, lock overlay, move-matching scope resolution, sort scope resolution).
3. Replace the keybind toggle's `SortLockC2SPayload.send(...)` chain with `IPSlotState.SORT_LOCK.set(slot, !IPSlotState.SORT_LOCK.get(slot))`.
4. Delete `ClientLockStateHolder.java`, `ServerLockStateHolder.java`, `SortLockC2SPayload.java`, `SlotLockState.java`, the server-side receiver registration in `InventoryPlus.init`.
5. F1 (player-slot persistence) and F2 (chest-lock visibility) fall out automatically.

**13e-2: F3 full-lock (feature, not migration).** New channel `IPSlotState.FULL_LOCK` with a distinct keybind. Click-protocol integration to block PICKUP on full-locked slots — orthogonal to M1's primitive work.

**Other consumers.** Shulker-palette, sandboxes, agreeable-allays have no per-slot state; no migration.

---

## 9. Verification plan

### 9.1 Math / API — runs via `/mkverify all`

Region math for M5 added a sixth contract probe; M1 adds a seventh:

- Register a test channel `("menukit-verify", "test_bool")` with `Codec.BOOL` default false.
- Query a synthetic `SlotIdentity` from the test handler's slots → reads default (false). PASS.
- Write true to the channel via the slot-less API against a synthetic `PersistentContainerKey.PlayerInventory(testPlayerUUID)`. Read back → true. PASS.
- Query the same channel via the `Slot` overload after opening the test screen → true. PASS.
- Verify persistence-across-open: write, close the test menu, reopen → still true (server-side attachment persists across close/open; menu-open snapshot repopulates the client cache).
- Verify wire round-trip: write via the slot-based API on the verify-harness server thread, confirm the subsequent `SlotStateUpdateS2CPayload` was issued to the expected target (spy packet listener in the verify harness).
- Tag-level inspection: after writing `true`, read the `Tag` directly from the attachment bag → verify it's `ByteTag.ONE` (the NbtOps encoding of `Codec.BOOL` true). This confirms the "match vanilla's persistence patterns" discipline empirically — the stored Tag is what `/data get` would return.

**Cleanup discipline.** All probe writes use the `"menukit-verify:*"` namespace. At the start of each `/mkverify all` run, the harness clears every channel under that namespace from the test player's attachment. This keeps probes idempotent — repeated runs don't accumulate state in the player's save. Consumer channels in other namespaces are untouched.

### 9.2 Integration-level (dev client)

**F1 path.** Lock slot 15 in player inventory via keybind. `/mkverify` confirms the channel reports true server-side. Disconnect → reconnect. The join snapshot fires automatically; lock overlay still shows on slot 15 without re-toggling. PASS.

**F2 path.** Place a chest. Lock slot 4 in that chest. Walk far enough to unload the chunk, return. Open the chest → menu-open snapshot fires; slot 4's lock overlay still shows. PASS.

**Block-break cleanup.** Lock a slot in a chest. Break the chest. Place a new chest in the same position. Open the new chest → slot 4 is NOT locked (BE destroyed → attachment gone). PASS.

**Entity mobility.** Saddle a donkey. Lock a slot in its inventory. Ride the donkey to another dimension (via nether portal). Open donkey inventory → lock persists. PASS.

**Creative inventory sharing.** Lock a slot via the creative inventory's bottom-row inventory slots (which reference `player.getInventory()` per §4.1). Close creative, switch to survival. Open survival inventory → slot is locked. PASS. No `canonicalSlot` mapping required — the same `Container` instance is referenced by both menus.

**Respawn path.** Lock a player-inventory slot. Die in a lava pool. Respawn. Open inventory → lock persists (respawn snapshot re-delivers state to the newly-constructed InventoryMenu).

### 9.3 Cross-mod path

No cross-mod consumer in v1 — Shulker-palette's per-item state is out of scope. Verify that a second hypothetical consumer registering a separate channel (e.g., a test "annotation" channel) doesn't collide with IP's sort-lock channel. Two channels, same slot — independent reads/writes.

### 9.4 Vanilla-client path

Connect a vanilla (non-MenuKit) client to a MenuKit-aware server. Log on server-side that no snapshot was sent (channel handler didn't fire for that client). Verify server-stored state is intact after the vanilla client disconnects — i.e., the server didn't corrupt state just because it couldn't deliver it.

---

## 10. Resolved design decisions

The eight questions open during round-1 are now resolved per advisor review. Divergence during implementation requires a follow-up review; otherwise implement as below.

1. **Unregistered channel lookup → return default.** Matches Fabric attachment behavior. Defensive reads without try/catch. Throwing would be a correctness trap when mods unload mid-session. Specifically: `MKSlotState.getChannel(id)` returns `Optional<SlotStateChannel<?>>`; a consumer that holds a `SlotStateChannel<T>` directly always has a valid channel (registration returned it).
2. **Modded container registration → optional.** Unknown containers fall back to session-scope-only (matches today's `ClientLockStateHolder` behavior for every container type). Mandatory registration would break Phase-11 parity for mods that haven't opted in.
3. **Snapshot packet → bundle.** One packet per menu open / player join, all channels all slots. Rule of Three reconsiders if a future consumer has a fat schema that makes per-channel packets reasonable.
4. **`PersistentContainerKey.Modded` payload → `CompoundTag`.** Per THESIS.md principle 6 (match vanilla's persistence patterns). Mods fully own their key serialization as NBT; library stays opaque; `/data get` can inspect everything the library stores.
5. **Ender chest → separate sealed-interface variant.** `PlayerEnderChestContainer` is a different Container class with cross-dimensional semantics and a different persistence namespace. Merging into `PlayerInventory` with a slot-index namespace would be a hack.
6. **Client slot-less writes → server-only.** Client API is slot-based only. Server API has both slot-based and slot-less overloads. Clean boundary — clients never need to synthesize a persistent key.
7. **Channel default → required.** Every read returns a value. Optional-typed reads push complexity to call sites for no gain. Consumers that want "null is a meaningful absence" encode that in the codec (`Codec<Optional<T>>`) rather than making every read site handle emptiness.
8. **F3 full-lock → Phase 13 scope.** M1 is the persistence primitive; F3 is click-blocking semantics layered on M1's channel mechanism. Confirmed separate.

---

## 11. Non-goals / out of scope

- **Per-item state.** `CUSTOM_DATA` on ItemStack is the established mechanism; M1 does not duplicate. Shulker-palette's palette flag lives on the item, not the slot.
- **Shared lock state across players (V2+).** V1 stores per-player-per-slot marks — two players opening the same chest have independent marks. A V2 `Visibility` parameter on channel registration (`PRIVATE` default, `SHARED` opt-in) is the migration path. The `Map<UUID, Bag>` storage shape in §4.2 supports the migration without rewriting storage.
- **Per-player GC on BE attachments (V2+).** V1 accumulates per-player entries as players open BEs; no TTL, no cleanup pass. Acceptable for single-player use; flagged for V2 if public release surfaces pressure. Policy-based pruning (expire entries older than N days, cap entries per BE) is future work.
- **Cross-world persistence for block entities.** State is dimension-scoped. A BE exists in exactly one dimension; moving a BE across dimensions is not a vanilla operation.
- **Block-move preservation.** Breaking and placing a chest does not preserve state. The block is destroyed; the state goes with it. Corollary: mods that allow "moving" a block with contents (Carpet, etc.) do not preserve M1 state unless they explicitly copy the attachment — that's their concern.
- **Automatic ItemStack-level migration.** State stored on a slot does not transfer to an item if the slot's contents get dropped / picked up. Slot state and item state are separate concerns.
- **Legacy migration.** Phase 12 ships M1; Phase 13 rewrites IP's lock holders against M1 with no migration path. Any lock state in memory from a prior build is lost — the migration commit is a full rewrite. (Players' live locks are session-scoped today anyway; losing them on mod update is current behavior.)
- **Multi-server sync.** State persists per-world on the server; moving a player's save between servers moves their player attachments but not the BE attachments of world A to world B. Server-cluster-aware persistence is out of scope.
- **Authorization for modded auto-actions.** No "permission to lock a slot" model. Any client that calls `channel.set(slot, ...)` for a slot in its open menu writes the value. Server trusts client writes in v1. V2+ concern if shared-state lands.
- **Querying state outside an open menu (client).** The slot-based API requires an open menu (to resolve `slot.container` → persistent key). The slot-less API (`channel.get(persistentKey, index)`) is for server-side tooling only — clients can't synthesize a persistent key outside a menu session.
- **Channel removal / deprecation.** Once a channel registers in a session, it stays registered. Attachment payloads for un-registered (but previously-registered) channels sit dormant as opaque `Tag` values; no GC. V2+ if channel churn becomes real.

---

## 12. Library vs consumer boundary

**M1 provides:**
- The typed-channel registration machinery.
- Persistent storage via Fabric attachments for four built-in container types.
- Menu-open snapshot sync + per-mutation broadcast.
- Modded container extension points.
- Session-scoped client cache (internal implementation detail; consumers see stable read/write API).

**Consumers provide:**
- Channel registration (`MKSlotState.register`).
- Call sites for read / write.
- Handling of the channel's semantic meaning (what "locked" means for IP is IP's concern — M1 just stores a boolean).
- Modded container resolvers, if applicable.

**Library does NOT provide:**
- Pre-built channels (no `MKSlotState.LOCKED` or similar — every channel is consumer-declared).
- Per-slot UI primitives for rendering state (LockOverlayDecoration stays in IP).
- Keybind integration (consumer-owned — KeybindDispatch stays in IP).

---

## 13. Round-1 review — closed

All round-1 open questions are resolved in §10. Advisor pass complete; implementation can proceed.

Key round-1 shifts captured above:

- **Tag-native storage** replaces the initial `byte[]` serialization throughout (§4.2, §6, §7). THESIS.md principle 6 added to make this a library-wide discipline, not a one-off decision.
- **Player-join + respawn snapshot path** added as a peer to menu-open (§4.3, §6.2a). Without it, F1 breaks because `InventoryMenu` never routes through `openMenu`.
- **Slot-index distinction** made explicit — `menuSlotIndex` vs `containerSlotIndex` named throughout §4.3 and §6 with translation boundaries visible.
- **Creative-mode source finding** documented in §4.1 with vanilla-source evidence — both menus share `player.getInventory()`, no canonicalSlot mechanism needed.
- **Storage-shape rationale** articulated in §4.2 with the V2 shared-state migration path explicit. The `Map<UUID, Bag>` shape is a V2-ready design choice, not a v1 convenience.
- **`Map<UUID, Bag>` V2 migration path** flagged as a future-work hook (channel `Visibility` parameter).
- **Channel registration** switched from "idempotent on parameter equality" to "first-registration-wins + warning" (§4.6) — value equality on `Codec` instances is unreliable.
- **`/mkverify` cleanup** via `"menukit-verify:*"` namespace (§9.1) so probe runs don't accumulate state.
- **By-reference-to-owner pattern** named in §4.1 alongside M5's by-value/by-reference framing. Three composition patterns now catalogued.

---

## 14. Summary

M1 ships a typed-channel per-slot state primitive that persists via Fabric attachments on the natural owner (Player for inventory + ender chest, BlockEntity for chest/furnace/etc., Entity for donkey/minecart, plus a modded extension). State is stored as `Tag` internally (NBT-native, `/data get`-inspectable), serialized via the channel's `Codec<T>` with NbtOps. Consumers register channels at mod init with an `Identifier`, `Codec<T>`, `StreamCodec<RegistryFriendlyByteBuf, T>`, and default value. Reads and writes go through `channel.get(slot)` / `channel.set(slot, value)`; the library dispatches side-appropriately. Server is authoritative; client writes round-trip through a C2S packet; server broadcasts back.

**Two snapshot paths, same packet type.** Menu-open snapshot fires post-`openMenu` for any container-backed menu (chests, donkeys, peek sessions). Player-join snapshot fires on `ServerPlayConnectionEvents.JOIN` and on respawn post-construction — covering the player's own `InventoryMenu` which never routes through `openMenu`. Without the second path, F1 breaks.

Cleanup is driven by attachment lifetime (player save, BE NBT, entity NBT) — no library-level GC needed. State is **by-reference-to-owner** (§4.1): owner dies, state dies. Structural ownership means structural cleanup.

**Storage shape designs for V2 shared-state.** Block-entity attachments hold `Map<PlayerUUID, SlotStateBag>` in v1 (per-player private). A V2 `Visibility` parameter on channel registration (`PRIVATE`/`SHARED`) migrates shared channels out of the UUID layer into a BE-level map — one change to the attachment schema, no data migration.

Phase 13 migrates IP's `SortLock*Holder` / `SortLockC2SPayload` / `SlotLockState` trio to a single `IPSlotState.SORT_LOCK` channel declaration. F1 (persistence) and F2 (chest-lock across reopens) fall out automatically. Full-lock (F3) is a separate Phase 13 feature reusing M1 as its persistence layer.

**Status: resolved — ready for implementation.** Implementation can proceed on library scaffolding (channel registration machinery, packet types, attachment registration, two snapshot handlers) in parallel with any remaining doc polish — the revisions tightened the flesh, not the skeleton.
