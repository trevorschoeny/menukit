5.1.0: Every operation in the game can be listed, named, scoped, and blocked. Adds API and breaks nothing.

Vanilla's operations are split as far as they go. Eleven keys, one per thing a player can do to a slot: `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT`, `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `WORLD_PICKUP`. A plain click takes, puts, or both when it swaps. They are `BehaviorKeys.VANILLA_OPERATIONS`. Every one is on by default. MenuKit now enforces them itself at vanilla's own seams, for every slot kind, so a mod that depends on MenuKit alone gets the enforcement without Containers.

Operations have names. `SlotOperations.name(op)` and `description(op)` return translatable text on `slot_operation.<namespace>.<path>` and `.description`, and a mod ships the lines for its own operations in its lang file. A settings screen can list every operation that exists with words a player can read.

A lock is a veto. `SlotOperations.veto(rule)` registers a rule that can only say no, laid over whatever a slot's author declared. `SlotOperations.allows(menu, slot, player, op)` is the one question: the cascade says yes and no veto says no. Every vanilla seam asks it, and an operation a mod adds should ask it too. A veto sees a `SlotRef` with the container and index, the live slot and menu, the acting player, and the slot's category.

A click a mod sends counts as the operation it serves. `SlotOperations.as(take, put, clicks)` tags every click sent inside it, and the vetoes judge the tag's operation instead of the gesture, so a lock that blocks shift-click does not block a restock that shift-clicks. The slot's author still judges the gesture. The tag reaches the integrated server. A click sent without a tag counts as the gesture it looks like.

The client refuses to send a click its own slot refuses. A plain click, shift-click out, drop, swap, or drag onto a refused slot never leaves the client, so it holds on any server.

Vanilla slots get the group and category rungs. A category's inherent operations reached created slots only in 5.0.0. Now `allows` reads a vanilla slot's category from its menu, so `inherent(PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)` keeps shift-clicks out of the hotbar and lets them into the 9x3. `WindowEngine.resolve` gains an overload that takes memberships the caller states.

Deprecated: nothing in MenuKit itself. Containers' `QUICK_MOVE` is deprecated in its own changelog.

5.0.0: Slots now say what they are and what may be done to them, and placement names its parts. Three changes, one of them breaking.

Slot groups are a registry. Created slot groups appear next to the vanilla categories, and the registry answers without a menu open. `SlotGroupCategories.all()` lists every category that exists. `SlotGroupCategories.of(menu)` lists the slots in each category on an open menu. `SlotGroupCategories.categoriesBySlot(menu)` answers per slot. A mod mints its own category when no vanilla one fits.

Operations are a named, open vocabulary. An operation is a bulk or shortcut action on a slot. Vanilla ships three: shift-click (`QUICK_MOVE`), double-click collect (`COLLECT`), and drag-fill (`DRAG_FILL`). Any mod defines its own operation as a `BehaviorKey` and publishes it with `SlotOperations.define`, so other mods find it through `SlotOperations.all()`. A slot resolves an operation from its own declaration first, then its group, then the group's category, then the key's default. That order is specificity, not load order, so two mods declaring at different levels no longer depend on which loaded first. `SlotOperations.inherent(category, operation, value)` sets a category-wide answer once. `COLLECT` and `DRAG_FILL` are window keys on any slot kind; Containers enforces them at vanilla's own seams.

Placement has two named parts. A reference is the rectangle a panel is measured against. A region is where the panel sits relative to it. Four region enums are now two, named for the vocabulary instead of the reference they implied. `OutsideRegion` holds the eleven placements outside a rectangle. `InsideRegion` holds the nine spots on one. `Reference` replaces `ScreenBounds` and `SlotGroupBounds`, which were the same record.

A panel anchored to a slot group is measured from that one group, not from every slot sharing its category. A created group can no longer move a panel anchored to a vanilla one. `SlotGroupPanelAdapter.onGroup(SlotGroupId...)` anchors to a created group by id.

Breaking: `MenuRegion` and `SlotGroupRegion` are `OutsideRegion`. `HudRegion` and `ScreenRegion` are `InsideRegion`. `ScreenBounds` and `SlotGroupBounds` are `Reference`. `HudRegion.CENTER_CROSSHAIR_CLEARANCE` is `RegionConstants.CENTER_CROSSHAIR_CLEARANCE`. Replace the type names; the constant names inside each enum are unchanged. The registry and operations add API and break nothing on their own.
