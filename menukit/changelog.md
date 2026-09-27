5.2.0 (unreleased): Tabs. `Tabs` is a tab strip with the selected tab's body below it, for tabbed settings menus. It wraps into rows or scrolls sideways, aligns tabs left, centred, right or to fill each row, hides tabs live, and keeps each tab's scroll position. Selection is your own field, so opening a menu on a given tab is setting it first. Elements can now take their height from their panel (`fillsHeight()`), which is how a `Tabs` fills a full screen. Other mods add tabs to a named menu with `Tabs.addTo`: an owner's `standIn()` tab holds the place of a tab another mod may add and shows dimmed until one does, other added tabs go `after` or `before` a tab, and the order never depends on which mod loaded first. `Checkbox.linked` reads its checked state from your own getter every frame and writes through your setter, so a setting changed somewhere else shows at once; `Toggle.linked` and `Checkbox.linked` both take a `disabledWhen` that greys the control out live. Adds API and breaks nothing.

5.1.0: An inventory mod can now list every slot operation in the game, show each one to the player by name, and block the ones they pick on the slots they pick. This release adds API and breaks nothing.

Vanilla's slot actions are split into one operation per gesture: click to take, click to place, shift-click out, shift-click in, double-click collect, drag fill, hotbar swap, offhand swap, drop one, drop stack, and item pickup. All of them are on by default. MenuKit enforces them itself, so a mod that depends on MenuKit alone gets them without Containers. Q, Ctrl-Q and F count as the same operations when no screen is open.

Every operation has a display name and a one-line description, read from lang keys (`slot_operation.<namespace>.<path>`), so a settings screen can list them. `SlotOperations.all()` returns vanilla's operations and every operation other mods have defined.

A lock is a veto. `SlotOperations.veto(rule)` refuses an operation on a slot and never overrides what the slot's own mod declared. `SlotOperations.allows(...)` is the single check that vanilla's seams use and that your own operations should use too. A veto knows which player is acting, so a per-player lock can tell a LAN guest's click from the host's.

Clicks your mod sends count as your operation when you wrap them in `SlotOperations.as(...)`. A lock that blocks shift-click then leaves alone the restock that happens to use one. A click sent without the wrapper counts as the gesture it looks like.

When a slot refuses a click, the client does not send it, so the refusal also holds on a server that does not run MenuKit.

Rules set for a slot category now reach vanilla slots. `SlotOperations.inherent(PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)` keeps shift-clicked items out of the hotbar and still lets them into the main inventory.

Slot groups can be listed from the title screen with `SlotGroups.all()`. Each group has a player-facing name and a stable id you can save. `SlotGroupSet` shows several groups as one row, which suits a mod that adds many small groups of the same kind.
