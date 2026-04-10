package gui;

/**
 * 2D size vector where each axis is composed of an absolute pixel offset
 * and a screen-relative scale fraction.
 *
 * <p>Resolved pixels on an axis: {@code offset + scale * screenDimension}
 *
 * <ul>
 *   <li>Pure offset  → fixed pixel size, unaffected by aspect ratio</li>
 *   <li>Pure scale   → proportional to the screen dimension on that axis</li>
 *   <li>Mixed        → fixed base with a proportional addition</li>
 * </ul>
 *
 * <pre>
 *   // 200 px wide × 200 px tall — never distorted
 *   GuiVec2.ofOffset(200, 200)
 *
 *   // 34% of screen width × 34% of screen height — scales with window
 *   GuiVec2.ofScale(0.34f, 0.34f)
 *
 *   // 64 px base + 10% of screen width, same for height
 *   new GuiVec2(64, 0.10f, 64, 0.10f)
 * </pre>
 */
public class GuiVec2 {

    /** Fixed pixel contribution on each axis. */
    public float xOffset, yOffset;
    /** Screen-fraction contribution on each axis (0-1). */
    public float xScale, yScale;

    public GuiVec2() {}

    public GuiVec2(float xOffset, float xScale, float yOffset, float yScale) {
        this.xOffset = xOffset;
        this.xScale  = xScale;
        this.yOffset = yOffset;
        this.yScale  = yScale;
    }

    /** Pure screen-relative scale; no fixed pixel offset. */
    public static GuiVec2 ofScale(float xScale, float yScale) {
        return new GuiVec2(0, xScale, 0, yScale);
    }

    /** Pure fixed-pixel size; unaffected by screen dimensions. */
    public static GuiVec2 ofOffset(float xOffset, float yOffset) {
        return new GuiVec2(xOffset, 0, yOffset, 0);
    }

    /** Resolves the final pixel width given the canvas width. */
    public int resolveX(int screenWidth) {
        return Math.round(xOffset + xScale * screenWidth);
    }

    /** Resolves the final pixel height given the canvas height. */
    public int resolveY(int screenHeight) {
        return Math.round(yOffset + yScale * screenHeight);
    }
}
