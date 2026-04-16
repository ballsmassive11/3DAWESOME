package gui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parses a BMFont {@code .fnt} descriptor (regular SDF or MSDF) and renders
 * coloured, scaled strings onto a {@link Graphics2D} context.
 *
 * <h3>SDF quality at any size</h3>
 * The raw SDF atlas is stored unthresholded.  A scale-aware smoothstep is
 * applied when a coloured atlas is first requested for a given (scale, colour)
 * pair, so edges remain crisp whether the text is 12 px or 200 px tall.
 * Results are cached, so the per-pixel work only happens once per unique
 * combination.
 *
 * <h3>MSDF support</h3>
 * The median of the R, G, B channels is used as the SDF distance value,
 * which is correct for both single-channel and multi-channel (MSDF) atlases.
 * Single-channel atlases have R=G=B, so the median equals any channel.
 */
public class BitmapFont {

    // ------------------------------------------------------------------ //
    // Glyph descriptor                                                    //
    // ------------------------------------------------------------------ //

    public static class Glyph {
        public final int x, y, width, height;
        public final int xOffset, yOffset, xAdvance;

        Glyph(int x, int y, int w, int h, int xo, int yo, int xa) {
            this.x = x;  this.y = y;  this.width = w;  this.height = h;
            this.xOffset = xo;  this.yOffset = yo;  this.xAdvance = xa;
        }
    }

    // ------------------------------------------------------------------ //
    // Cache key                                                           //
    // ------------------------------------------------------------------ //

    private static final class CacheKey {
        final Color color;
        final float scaleBucket;   // scale quantised to nearest 0.1

        CacheKey(Color c, float s) { color = c; scaleBucket = s; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof CacheKey)) return false;
            CacheKey k = (CacheKey) o;
            return Float.compare(k.scaleBucket, scaleBucket) == 0 && Objects.equals(k.color, color);
        }

        @Override public int hashCode() {
            return Objects.hash(color, scaleBucket);
        }
    }

    // ------------------------------------------------------------------ //
    // Fields                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Raw SDF stored in the alpha channel (0 = far outside, 255 = far inside).
     * RGB channels are set to white and ignored; colour is applied at cache time.
     */
    private final BufferedImage rawAtlas;

    /** Coloured + scale-thresholded atlas, built once per (scale, colour) pair. */
    private final Map<CacheKey, BufferedImage> atlasCache = new HashMap<>();

    private final Map<Integer, Glyph> glyphs    = new HashMap<>();
    private final int lineHeight;
    private final int base;
    private final int fontSize;

    /**
     * SDF units per atlas pixel, derived from the padding declared in the
     * .fnt file.  For padding=8: rate = 0.5 / 8 = 0.0625.
     * (0.5 because the range spans from 0→edge and edge→1.)
     */
    private final float sdfRate;

    // ------------------------------------------------------------------ //
    // Construction                                                        //
    // ------------------------------------------------------------------ //

    public BitmapFont(String fntPath) {
        String atlasPath = null;
        int lh = 0, b = 0, fs = 0, padding = 8;

        try (InputStream is = openResource(fntPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("info ")) {
                    fs = parseFntInt(line, "size");
                    // padding=top,right,bottom,left — all equal for symmetric SDF
                    String padStr = parseFntString(line, "padding");
                    if (!padStr.isEmpty()) {
                        try { padding = Integer.parseInt(padStr.split(",")[0]); }
                        catch (NumberFormatException ignored) {}
                    }
                } else if (line.startsWith("common ")) {
                    lh = parseFntInt(line, "lineHeight");
                    b  = parseFntInt(line, "base");
                } else if (line.startsWith("page ")) {
                    String file = parseFntString(line, "file");
                    String dir  = fntPath.substring(0, fntPath.lastIndexOf('/') + 1);
                    atlasPath   = dir + file;
                } else if (line.startsWith("char ")) {
                    int id = parseFntInt(line, "id");
                    glyphs.put(id, new Glyph(
                        parseFntInt(line, "x"),       parseFntInt(line, "y"),
                        parseFntInt(line, "width"),   parseFntInt(line, "height"),
                        parseFntInt(line, "xoffset"), parseFntInt(line, "yoffset"),
                        parseFntInt(line, "xadvance")
                    ));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load font: " + fntPath, e);
        }

        this.lineHeight = lh;
        this.base       = b;
        this.fontSize   = fs;
        this.sdfRate    = 0.5f / Math.max(padding, 1);

        if (atlasPath == null) throw new RuntimeException("No page entry in: " + fntPath);

        try (InputStream atlasIs = openResource(atlasPath)) {
            BufferedImage raw = ImageIO.read(atlasIs);
            if (raw == null) throw new IOException("Could not decode atlas");
            this.rawAtlas = extractRawSdf(raw);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load atlas: " + atlasPath, e);
        }
    }

    // ------------------------------------------------------------------ //
    // Drawing                                                             //
    // ------------------------------------------------------------------ //

    public void drawString(Graphics2D g2, String text, int x, int y, float scale, Color color) {
        if (text == null || text.isEmpty()) return;

        BufferedImage atlas = cachedAtlas(color, scale);
        Composite     prev  = g2.getComposite();
        Object        prevInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);

        g2.setComposite(AlphaComposite.SrcOver);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            Glyph gl = glyphs.get((int) text.charAt(i));
            if (gl == null) continue;
            if (gl.width == 0 || gl.height == 0) {
                cursor += Math.round(gl.xAdvance * scale);
                continue;
            }

            int dx = cursor + Math.round(gl.xOffset * scale);
            int dy = y      + Math.round(gl.yOffset * scale);
            int dw = Math.round(gl.width  * scale);
            int dh = Math.round(gl.height * scale);

            g2.drawImage(atlas,
                dx, dy, dx + dw, dy + dh,
                gl.x, gl.y, gl.x + gl.width, gl.y + gl.height,
                null);

            cursor += Math.round(gl.xAdvance * scale);
        }

        g2.setComposite(prev);
        if (prevInterp != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, prevInterp);
        }
    }

    // ------------------------------------------------------------------ //
    // Measurement                                                         //
    // ------------------------------------------------------------------ //

    public int measureWidth(String text, float scale) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            Glyph g = glyphs.get((int) text.charAt(i));
            if (g != null) total += Math.round(g.xAdvance * scale);
        }
        return total;
    }

    public float scaleForPixelHeight(float pixelHeight) { return pixelHeight / lineHeight; }

    public int getLineHeight() { return lineHeight; }
    public int getBase()       { return base; }
    public int getFontSize()   { return fontSize; }

    // ------------------------------------------------------------------ //
    // Atlas building                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Reads the raw SDF from the source image using the <em>median</em> of the
     * R, G, B channels.  This correctly handles both single-channel SDF
     * (R=G=B, median = any channel) and MSDF (channels differ, median is the
     * correct reconstruction formula).
     *
     * <p>The result is stored in the alpha channel of a white ARGB image so
     * that colour tinting is trivial later.
     */
    private static BufferedImage extractRawSdf(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getRGB(0, 0, w, h, pixels, 0, w);

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >>  8) & 0xFF;
            int b =  p        & 0xFF;
            int sdf = median3(r, g, b);          // correct for MSDF and SDF
            pixels[i] = (sdf << 24) | 0x00FFFFFF;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    /**
     * Builds a coloured atlas with a scale-appropriate SDF threshold.
     *
     * <p>The smoothstep spread is computed so that it spans exactly ±0.5
     * screen pixels at the glyph edge, giving crisp, anti-aliased results
     * regardless of the render scale.  Larger scales produce a narrower
     * (sharper) spread; smaller scales produce a wider (smoother) spread.
     */
    private BufferedImage buildColoredAtlas(Color color, float scale) {
        // SDF units that correspond to one screen pixel at this scale
        float pixelSdf = sdfRate / scale;
        // Use ±0.5 screen-pixel window → ensures exactly one pixel of AA
        float spread = pixelSdf * 0.5f;
        float lo = Math.max(0f, 0.5f - spread);
        float hi = Math.min(1f, 0.5f + spread);

        int w  = rawAtlas.getWidth(), h = rawAtlas.getHeight();
        int cr = color.getRed(), cg = color.getGreen(), cb = color.getBlue();
        int rgb = (cr << 16) | (cg << 8) | cb;

        int[] pixels = new int[w * h];
        rawAtlas.getRGB(0, 0, w, h, pixels, 0, w);

        for (int i = 0; i < pixels.length; i++) {
            float sdf   = ((pixels[i] >> 24) & 0xFF) / 255f;
            float alpha = smoothstep(lo, hi, sdf);
            int   ia    = Math.round(alpha * 255f);
            pixels[i] = (ia << 24) | rgb;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    private BufferedImage cachedAtlas(Color color, float scale) {
        // Quantise scale to nearest 0.1 to keep the cache small
        float bucket = Math.round(scale * 10f) / 10f;
        return atlasCache.computeIfAbsent(new CacheKey(color, bucket),
            k -> buildColoredAtlas(k.color, k.scaleBucket));
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                             //
    // ------------------------------------------------------------------ //

    private static int median3(int a, int b, int c) {
        if (a > b) { int t = a; a = b; b = t; }
        if (b > c) { int t = b; b = c; c = t; }
        if (a > b) { int t = a; a = b; b = t; }
        return b;
    }

    private static float smoothstep(float lo, float hi, float t) {
        t = Math.max(0f, Math.min(1f, (t - lo) / (hi - lo)));
        return t * t * (3f - 2f * t);
    }

    private static InputStream openResource(String path) throws IOException {
        InputStream is = BitmapFont.class.getResourceAsStream(path);
        if (is != null) return is;
        File f = new File(path.startsWith("/") ? "src" + path : path);
        if (f.exists()) return new FileInputStream(f);
        throw new IOException("Resource not found: " + path);
    }

    private static int parseFntInt(String line, String key) {
        int ki = line.indexOf(key + "=");
        if (ki < 0) return 0;
        int start = ki + key.length() + 1;
        int end   = start;
        while (end < line.length() &&
               (Character.isDigit(line.charAt(end)) || line.charAt(end) == '-')) end++;
        try { return Integer.parseInt(line.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String parseFntString(String line, String key) {
        int ki = line.indexOf(key + "=");
        if (ki < 0) return "";
        int start = ki + key.length() + 1;
        if (start < line.length() && line.charAt(start) == '"') {
            int end = line.indexOf('"', start + 1);
            return end > start ? line.substring(start + 1, end) : "";
        }
        int end = start;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
        return line.substring(start, end);
    }
}
