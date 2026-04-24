package gui.text;

import gui.vec.Vector2;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import com.sun.j3d.utils.image.TextureLoader;

import javax.media.j3d.J3DGraphics2D;
import javax.media.j3d.Texture2D;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class GuiText {

    private static int shaderProgram = -1;
    private static int fontTextureId = -1;
    private static BitmapFont currentFont;

    private String text;
    private BitmapFont font;
    private Vector2 position;
    private float pixelHeight = 32f;
    private float letterSpacing = 0f; // extra pixels between characters
    private Color color = Color.WHITE;
    private boolean visible = true;

    public GuiText(BitmapFont font, String text, Vector2 position) {
        this.font = font;
        this.text = text;
        this.position = position;
    }

    public void setPixelHeight(float height) { this.pixelHeight = height; }
    public void setLetterSpacing(float spacing) { this.letterSpacing = spacing; }
    public float getLetterSpacing() { return letterSpacing; }
    public void setColor(Color color) { this.color = color; }

    public void draw(J3DGraphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible || text == null || text.isEmpty()) return;

        GL2 gl = GLContext.getCurrent().getGL().getGL2();
        initShader(gl);
        initTexture(gl);

        gl.glUseProgram(shaderProgram);
        
        // Save current matrix state
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        // Set texture uniform
        int texLoc = gl.glGetUniformLocation(shaderProgram, "fontTexture");
        if (texLoc != -1) gl.glUniform1i(texLoc, 0);
        gl.glActiveTexture(GL2.GL_TEXTURE0);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, fontTextureId);

        // Set smoothing uniform
        int smoothLoc = gl.glGetUniformLocation(shaderProgram, "smoothing");
        // A rough heuristic for smoothing: 0.1 at 57px font size
        float smoothing = 0.1f * (57f / pixelHeight); 
        if (smoothLoc != -1) gl.glUniform1f(smoothLoc, smoothing);

        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_CULL_FACE);
        gl.glEnable(GL2.GL_TEXTURE_2D);

        gl.glPushAttrib(GL2.GL_VIEWPORT_BIT | GL2.GL_ENABLE_BIT);
        gl.glViewport(0, 0, screenWidth, screenHeight);

        int px = position.resolveX(screenWidth);
        int py = position.resolveY(screenHeight);
        float scale = pixelHeight / font.getLineHeight();

        float curX = px;
        // Text centering (crude)
        float totalWidth = 0;
        for (char c : text.toCharArray()) {
            BitmapFont.CharData charData = font.getChar(c);
            if (charData != null) totalWidth += charData.xadvance * scale + letterSpacing;
        }
        if (position.xScale > 0.49f && position.xScale < 0.51f) {
            curX -= totalWidth / 2f;
        }

        float curY = py + font.getBase() * scale;

        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, color.getAlpha()/255f);

        for (char c : text.toCharArray()) {
            BitmapFont.CharData charData = font.getChar(c);
            if (charData == null) charData = font.getChar('?');
            if (charData == null) continue;

            float x0 = curX + charData.xoffset * scale;
            float y0 = curY + charData.yoffset * scale;
            float x1 = x0 + charData.width * scale;
            float y1 = y0 + charData.height * scale;

            float u0 = (float)charData.x / font.getScaleW();
            float v0 = (float)charData.y / font.getScaleH();
            float u1 = (float)(charData.x + charData.width) / font.getScaleW();
            float v1 = (float)(charData.y + charData.height) / font.getScaleH();

            gl.glTexCoord2f(u0, v0); gl.glVertex2f(x0, y0);
            gl.glTexCoord2f(u1, v0); gl.glVertex2f(x1, y0);
            gl.glTexCoord2f(u1, v1); gl.glVertex2f(x1, y1);
            gl.glTexCoord2f(u0, v1); gl.glVertex2f(x0, y1);

            curX += charData.xadvance * scale + letterSpacing;
        }
        gl.glEnd();

        gl.glPopAttrib();

        // Restore matrix state
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopMatrix();

        gl.glUseProgram(0);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
    }

    private void initShader(GL2 gl) {
        if (shaderProgram != -1) return;

        int v = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
        int f = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);

        gl.glShaderSource(v, 1, new String[]{loadResource("/gui/font.vert")}, null);
        gl.glCompileShader(v);
        checkShaderLog(gl, v);
        
        gl.glShaderSource(f, 1, new String[]{loadResource("/gui/font.frag")}, null);
        gl.glCompileShader(f);
        checkShaderLog(gl, f);

        shaderProgram = gl.glCreateProgram();
        gl.glAttachShader(shaderProgram, v);
        gl.glAttachShader(shaderProgram, f);
        gl.glLinkProgram(shaderProgram);
        gl.glValidateProgram(shaderProgram);
    }

    private void initTexture(GL2 gl) {
        if (fontTextureId != -1 && currentFont == font) return;
        
        currentFont = font;
        String path = font.getTexturePath();
        // TextureLoader needs a path or BufferedImage
        // The path from BitmapFont is e.g. /fonts/arial/arial.png
        TextureLoader loader = new TextureLoader(GuiText.class.getResource(path), null);
        Texture2D tex = (Texture2D) loader.getTexture();
        
        // We need the raw OpenGL texture ID. Java3D might not give it easily,
        // but we can let TextureLoader load it and then use J3D's internal ID or just load it ourselves via GL.
        // Actually, let's load it with GL to be sure.
        
        // For simplicity, let's use the ImageIO and GL.
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(GuiText.class.getResourceAsStream(path));
            int[] ids = new int[1];
            gl.glGenTextures(1, ids, 0);
            fontTextureId = ids[0];
            gl.glBindTexture(GL2.GL_TEXTURE_2D, fontTextureId);
            
            // Standard texture setup
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            
            // Upload
            int w = img.getWidth();
            int h = img.getHeight();
            int[] data = new int[w * h];
            img.getRGB(0, 0, w, h, data, 0, w);
            
            // Convert ARGB to RGBA
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            buffer.order(java.nio.ByteOrder.nativeOrder());
            for (int i = 0; i < data.length; i++) {
                int argb = data[i];
                byte a = (byte)((argb >> 24) & 0xFF);
                byte r = (byte)((argb >> 16) & 0xFF);
                byte g = (byte)((argb >> 8) & 0xFF);
                byte b = (byte)((argb) & 0xFF);
                buffer.put(r).put(g).put(b).put(a);
            }
            buffer.flip();
            
            gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, w, h, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, buffer);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String loadResource(String path) {
        // First try to load from classpath directly
        InputStream is = GuiText.class.getResourceAsStream(path);
        if (is == null && path.startsWith("/resources/")) {
            // If it starts with /resources/, try stripping it because in Maven/IDE 
            // the contents of resources/ are often at the root of the classpath.
            is = GuiText.class.getResourceAsStream(path.substring(10));
        }
        if (is == null) {
            // Try class loader
            String cpPath = path.startsWith("/") ? path.substring(1) : path;
            is = GuiText.class.getClassLoader().getResourceAsStream(cpPath);
            if (is == null && cpPath.startsWith("resources/")) {
                is = GuiText.class.getClassLoader().getResourceAsStream(cpPath.substring(10));
            }
        }
        
        if (is == null) {
            System.err.println("[GuiText] Resource not found: " + path);
            throw new RuntimeException("Resource not found: " + path);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkShaderLog(GL2 gl, int shader) {
        int[] logLength = new int[1];
        gl.glGetShaderiv(shader, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
        if (logLength[0] > 1) {
            byte[] log = new byte[logLength[0]];
            gl.glGetShaderInfoLog(shader, logLength[0], null, 0, log, 0);
            System.err.println("Shader Log: " + new String(log));
        }
    }
}
