package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.slot.ResolvedSlotGroup;

/**
 * The rectangle a panel is placed against, a <b>reference</b>.
 *
 * <p>It is not the panel and has nothing to do with the panel's own size. It is
 * something already on screen that the panel is measured from. Three kinds exist,
 * and this one record carries all of them because a placement only ever needs the
 * four numbers:
 *
 * <table>
 *   <tr><th>Reference</th><th>What it is</th></tr>
 *   <tr><td>Menu frame</td><td>vanilla's GUI window for the open container</td></tr>
 *   <tr><td>Slot group</td><td>the rectangle enclosing one {@link ResolvedSlotGroup}</td></tr>
 *   <tr><td>Screen</td><td>the game window, for HUD panels and screen chrome</td></tr>
 * </table>
 *
 * <p>Which kind a placement uses is decided by the call site, a
 * {@link ScreenPanelAdapter} means the menu frame, a {@link SlotGroupPanelAdapter}
 * means the group it targets, so the reference does not need to be a tagged type
 * and a {@link com.trevlar.menukit.api.panel.OutsideRegion} does not need to name which
 * kind it applies to. That naming was the confusion this type retires: before
 * 5.0.0 there were two identical records, {@code ScreenBounds} and
 * {@code SlotGroupBounds}, and four region enums encoding the reference a second
 * time.
 *
 * <p>Values are passed per call rather than held, so an adapter stays decoupled
 * from {@code Screen} state and resize handling falls out for free: the caller
 * reads the current numbers each frame.
 *
 * @param leftPos     screen-space X of the rectangle
 * @param topPos      screen-space Y of the rectangle
 * @param imageWidth  its width
 * @param imageHeight its height
 */
public record Reference(int leftPos, int topPos, int imageWidth, int imageHeight) {
}
