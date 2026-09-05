# MenuKit: Containers

MenuKit: Containers is a slot extension for MenuKit. It lets mods add slots that persist, whether in custom container menus or placed where vanilla never had them.

What it does:
- Creates slots as UI components, placed like any other: a pocket, an extra equipment slot, a satchel, each real and server-synced.
- Adds custom container menus that integrate with MenuKit's panels.
- Attaches per-slot state to any slot: server-authoritative, auto-synced, and either per-player-private or shared across all viewers.
- Makes created slots behave like vanilla ones: identical in creative and survival, and grave-mod aware. On death they drop, keep with keepInventory, or destroy with Curse of Vanishing. Curse of Binding and XP Mending are optional.
- Shows a registered slot on every container screen (creative, survival, and foreign menus) without per-screen setup.
- Reaches placed containers: double-chest resolution, server-side reads with no open menu, and a client-capability check.
- Stores each slot's state on its natural owner (the player, block, entity, or item), readable with `/data get`.
- Exposes every slot it creates to any MenuKit consumer, so other mods can see and address your slots, and you theirs.

MenuKit: Containers runs on client and server and depends on MenuKit, which it pulls in automatically. Requires Fabric.

## Install

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    modImplementation 'maven.modrinth:menukit-containers:2.0.0+26.2'
    // MenuKit comes in transitively.
}
```

Declare both in `fabric.mod.json`:

```json
"depends": { "menukit": ">=2.0.0", "menukit-containers": ">=2.0.0" }
```

## Example

Nine synced pocket slots on every container screen, registered once from the common entry point:

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);

MKCContainerPanel.define("mymod:pockets")
        .at(MenuRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .addSlot(SlotSpec.at("pockets").count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

## Docs

The docs live in the MenuKit repository:

- [Getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md)
- [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md): created slots, addresses, slot state channels, storage.
- [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md): synced player slots, slot flags, custom menus, behavior by address.
- [Limits](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md)
- Reference: run `./gradlew javadoc` and open `build/docs/javadoc/index.html`.

The `validator-mkc` mod in the same workspace is the reference consumer, with compiling usage of created slots, per-slot state, death, binding, and mending.

## License

MIT. See `LICENSE`.

## Issues

[github.com/trevorschoeny/menukit-containers/issues](https://github.com/trevorschoeny/menukit-containers/issues).
