4.0.0: Vanilla draws every slot. MenuKit no longer runs a slot pass of its own on container screens. A created slot's panel writes the position into vanilla's Slot.x and Slot.y before the slot pass, and vanilla draws the item, count, hover and ghost icon for created and vanilla slots alike. The practical result: a mod that decorates slots through an extractSlot mixin now marks created slots too, without depending on MenuKit. A vanilla slot behind a visible opaque panel is inactive for the frame, so vanilla draws nothing there and no other mod's slot hook sees it; a panel hides what it covers through vanilla's own isActive switch instead of by painting over it.

Frame composition order on container screens is declared in one class, ContainerScreenLayers. Flow panels sit below the slot pass; overlay panels and the modal dim sit above it. Panels on recipe-book screens no longer render twice per frame, and the recipe-book render mixin is gone.

Breaking: CreatedSlotResolver now returns the in-menu Slot rather than a position. SlotRendering keeps only the frame helper. Update any code that called either.

3.1.0: Button and Toggle accept a secondary-click handler (onSecondaryClick) that receives a Click with the button and modifier state (right, middle, shift+right); clicks with no handler still fall through to vanilla. Both also take a per-frame tint supplier (tint) for consumer-driven state such as a pinned mode. No breaks.

3.0.0: the Java package is now com.trevlar.menukit (was com.trevorschoeny.menukit); update imports. MenuKit and MenuKit: Containers now share one repository and one set of docs at github.com/trevorschoeny/menukit. No class or method names changed.
