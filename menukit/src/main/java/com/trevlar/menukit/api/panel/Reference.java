package com.trevlar.menukit.api.panel;

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
 *   <tr><td>Anchor</td><td>a rectangle another library supplies per screen (a slot group's box, a button column)</td></tr>
 *   <tr><td>Screen</td><td>the game window, for HUD panels and screen chrome</td></tr>
 * </table>
 *
 * <p>The call site decides which kind a placement uses: a {@link ScreenPanelAdapter}
 * means the menu frame, an {@code AnchorPanelAdapter} the anchor it targets. So the
 * reference is not a tagged type, and a
 * {@link com.trevlar.menukit.api.panel.OutsideRegion} does not name which kind it
 * applies to. (Before 5.0.0 there were two identical records, {@code ScreenBounds}
 * and {@code SlotGroupBounds}, and four region enums that encoded the reference a
 * second time.)
 *
 * <p>Values are passed per call rather than held, so an adapter does not depend on
 * {@code Screen} state and a resize needs no handling: the caller reads the current
 * numbers each frame.
 *
 * @param leftPos     screen-space X of the rectangle
 * @param topPos      screen-space Y of the rectangle
 * @param imageWidth  its width
 * @param imageHeight its height
 */
public record Reference(int leftPos, int topPos, int imageWidth, int imageHeight) {
}
