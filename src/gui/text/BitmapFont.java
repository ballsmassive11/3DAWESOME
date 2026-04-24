package gui.text;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class BitmapFont {

    public static class CharData {
        public int id;
        public int x, y, width, height;
        public int xoffset, yoffset, xadvance;
    }

    private final String texturePath;
    private final Map<Integer, CharData> characters = new HashMap<>();
    private int lineHeight;
    private int base;
    private int scaleW, scaleH;

    public BitmapFont(String fntPath) {
        // Assume texture is in the same directory as .fnt if not specified otherwise
        // But the .fnt file says file="arial.png"
        String dir = fntPath.substring(0, fntPath.lastIndexOf('/') + 1);
        
        try (InputStream is = BitmapFont.class.getResourceAsStream(fntPath)) {
            if (is == null) throw new RuntimeException("Font file not found: " + fntPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            String texFile = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("common ")) {
                    lineHeight = getValue(line, "lineHeight");
                    base = getValue(line, "base");
                    scaleW = getValue(line, "scaleW");
                    scaleH = getValue(line, "scaleH");
                } else if (line.startsWith("page ")) {
                    texFile = getStringValue(line, "file");
                } else if (line.startsWith("char ")) {
                    CharData c = new CharData();
                    c.id = getValue(line, "id");
                    c.x = getValue(line, "x");
                    c.y = getValue(line, "y");
                    c.width = getValue(line, "width");
                    c.height = getValue(line, "height");
                    c.xoffset = getValue(line, "xoffset");
                    c.yoffset = getValue(line, "yoffset");
                    c.xadvance = getValue(line, "xadvance");
                    characters.put(c.id, c);
                }
            }
            this.texturePath = dir + texFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load font: " + fntPath, e);
        }
    }

    private int getValue(String line, String tag) {
        String search = tag + "=";
        int start = line.indexOf(search);
        if (start == -1) return 0;
        start += search.length();
        int end = line.indexOf(" ", start);
        if (end == -1) end = line.length();
        return Integer.parseInt(line.substring(start, end).trim());
    }

    private String getStringValue(String line, String tag) {
        String search = tag + "=\"";
        int start = line.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = line.indexOf("\"", start);
        return line.substring(start, end);
    }

    public CharData getChar(int id) {
        return characters.get(id);
    }

    public String getTexturePath() {
        return texturePath;
    }

    public int getLineHeight() {
        return lineHeight;
    }

    public int getBase() {
        return base;
    }

    public int getScaleW() {
        return scaleW;
    }

    public int getScaleH() {
        return scaleH;
    }
}
