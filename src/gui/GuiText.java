package gui;

import java.awt.*;

/**
 * A GUI element that renders a text string using a {@link BitmapFont} SDF atlas.
 *
 * <p>Position uses {@link GuiVec2} so text can be anchored at fixed pixel
 * positions, screen-relative positions, or a mix of both.  Horizontal
 * alignment is controlled via {@link Align}.
 *
 * <h3>Quick start</h3>
 * <pre>
 *   // 24 px white label, top-left at (20, 20)
 *   GuiText label = new GuiText(GuiCanvas.ARIAL, "Hello!", GuiVec2.ofOffset(20, 20));
 *   label.setPixelHeight(24);
 *   guiCanvas.addText(label);
 *
 *   // Centred heading at 10% down from top
 *   GuiText heading = new GuiText(GuiCanvas.ARIAL, "3D AWESOME",
 *       GuiVec2.ofScale(0.5f, 0.1f), 0.6f, new Color(255, 200, 80));
 *   heading.setAlign(GuiText.Align.CENTER);
 *   guiCanvas.addText(heading);
 * </pre>
 *
 * <h3>Scale vs pixel height</h3>
 * Use {@link #setScale(float)} to control the size relative to the atlas native
 * size (scale 1.0 ≈ 82 px line height for the supplied Arial atlas), or use
 * {@link #setPixelHeight(float)} for direct pixel-height control.
 */
public class GuiText {

    // ------------------------------------------------------------------ //
    // Alignment                                                           //
    // ------------------------------------------------------------------ //

    /** Horizontal alignment relative to the anchor position. */
    public enum Align { LEFT, CENTER, RIGHT }

    // ------------------------------------------------------------------ //
    // Fields                                                              //
    // ------------------------------------------------------------------ //

    private final BitmapFont font;
    private String  text;
    private GuiVec2 position;
    private float   scale;
    private Color   color;
    private Align   align        = Align.LEFT;
    private boolean visible      = true;
    private Color   outlineColor = null;
    private int     outlineWidth = 2;

    // ------------------------------------------------------------------ //
    // Constructors                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Full constructor.
     *
     * @param font     {@link BitmapFont} to render with
     * @param text     string to display
     * @param position screen position (offset + scale per axis via {@link GuiVec2})
     * @param scale    font scale; {@code 1.0} = native atlas size
     * @param color    text colour
     */
    public GuiText(BitmapFont font, String text, GuiVec2 position, float scale, Color color) {
        this.font     = font;
        this.text     = text;
        this.position = position;
        this.scale    = scale;
        this.color    = color;
    }

    /**
     * Convenience constructor — white text, scale 1.0.
     *
     * @param font     {@link BitmapFont} to render with
     * @param text     string to display
     * @param position screen position
     */
    public GuiText(BitmapFont font, String text, GuiVec2 position) {
        this(font, text, position, 1.0f, Color.WHITE);
    }

    // ------------------------------------------------------------------ //
    // Drawing                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Draws this text element.  Call from within {@link GuiCanvas#postRender()}.
     *
     * @param g2           target graphics context
     * @param canvasWidth  current canvas width in pixels
     * @param canvasHeight current canvas height in pixels
     */
    public void draw(Graphics2D g2, int canvasWidth, int canvasHeight) {
        if (!visible || text == null || text.isEmpty()) return;

        int px = position.resolveX(canvasWidth);
        int py = position.resolveY(canvasHeight);

        if (align == Align.CENTER) {
            px -= font.measureWidth(text, scale) / 2;
        } else if (align == Align.RIGHT) {
            px -= font.measureWidth(text, scale);
        }

        if (outlineColor != null) {
            int r = outlineWidth;
            for (int ox = -r; ox <= r; ox++) {
                for (int oy = -r; oy <= r; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    font.drawString(g2, text, px + ox, py + oy, scale, outlineColor);
                }
            }
        }

        font.drawString(g2, text, px, py, scale, color);
    }

    // ------------------------------------------------------------------ //
    // Convenience                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Sets the scale so that the line height equals {@code pixelHeight} screen pixels.
     *
     * @param pixelHeight desired line height in pixels
     */
    public void setPixelHeight(float pixelHeight) {
        this.scale = font.scaleForPixelHeight(pixelHeight);
    }

    /**
     * Returns the total rendered width of the current text string at the
     * current scale, in pixels.
     */
    public int getRenderedWidth() {
        return font.measureWidth(text, scale);
    }

    /**
     * Returns the rendered line height in pixels (full line, including descenders).
     */
    public int getRenderedHeight() {
        return Math.round(font.getLineHeight() * scale);
    }

    // ------------------------------------------------------------------ //
    // Getters / setters                                                   //
    // ------------------------------------------------------------------ //

    public String getText()            { return text; }
    public void   setText(String text) { this.text = text; }

    public GuiVec2 getPosition()           { return position; }
    public void    setPosition(GuiVec2 p)  { this.position = p; }

    /** Font scale; {@code 1.0} = native atlas size. See also {@link #setPixelHeight}. */
    public float getScale()              { return scale; }
    public void  setScale(float scale)   { this.scale = scale; }

    public Color getColor()              { return color; }
    public void  setColor(Color color)   { this.color = color; }

    public Align getAlign()              { return align; }
    public void  setAlign(Align align)   { this.align = align; }

    public boolean isVisible()           { return visible; }
    public void    setVisible(boolean v) { this.visible = v; }

    /** Sets an outline rendered behind the text. Pass {@code null} to disable. */
    public void setOutline(Color color, int width) { this.outlineColor = color; this.outlineWidth = width; }
    public Color getOutlineColor()                 { return outlineColor; }
    public int   getOutlineWidth()                 { return outlineWidth; }

    public BitmapFont getFont()          { return font; }
}
