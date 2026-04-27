package gui.overlay;

import gui.canvas.GuiCanvas;
import gui.components.GuiFrame;
import gui.components.TextButton;
import gui.core.GuiObject;
import gui.text.GuiText;
import gui.vec.Vector2;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen main menu overlay with "Create World" and "Controls" buttons.
 * Add to GuiCanvas via addObject(); call setVisible(false) or removeObject() to dismiss.
 */
public class MainMenu extends GuiObject {

    // Main view
    private final GuiFrame overlay;
    private final GuiText title;
    private final TextButton createWorldBtn;
    private final TextButton controlsBtn;

    // Controls view
    private final GuiFrame controlsBg;
    private final List<GuiText> controlsLines = new ArrayList<>();
    private final TextButton backBtn;

    private boolean showingControls = false;
    private Runnable onCreateWorld;

    public MainMenu() {
        // No overlay backdrop; show the 3D scene directly behind the menu
        overlay = null;

        // Title
        title = new GuiText(GuiCanvas.ARIAL, "3DAWESOME", Vector2.ofScale(0.5f, 0.28f));
        title.setPixelHeight(80f);
        title.setColor(Color.WHITE);
        title.setCentered(true);

        // Main menu buttons
        createWorldBtn = new TextButton("Create World", Vector2.ofScale(0.5f, 0.50f), Vector2.ofOffset(260, 60));
        controlsBtn    = new TextButton("Controls",     Vector2.ofScale(0.5f, 0.62f), Vector2.ofOffset(260, 60));

        createWorldBtn.setTextHeight(50);
        createWorldBtn.setTextLetterSpacing(-6f);

        // ---- Controls view ----
        controlsBg = new GuiFrame(Vector2.ofScale(0.5f, 0.5f), Vector2.ofOffset(520, 420), new Color(20, 20, 20, 230));
        controlsBg.setCentered(true);

        // Lines: [label, description] or [header]
        Object[][] lines = {
            { "CONTROLS" },
            { "W / A / S / D",   "Move" },
            { "Arrow Keys",      "Look" },
            { "Space",           "Jump" },
            { "F",               "Toggle fly" },
            { "/",               "Command bar" },
        };
        // Y offsets relative to screen center (negative = above center)
        int[] yOffsets = { -160, -90, -52, -14, 24, 62 };

        for (int i = 0; i < lines.length; i++) {
            String text = lines[i].length == 1
                    ? (String) lines[i][0]
                    : lines[i][0] + "  \u2014  " + lines[i][1];  // em-dash separator
            GuiText line = new GuiText(GuiCanvas.ARIAL, text,
                    new Vector2(0, 0.5f, yOffsets[i], 0.5f));
            line.setPixelHeight(i == 0 ? 30f : 22f);
            line.setColor(i == 0 ? Color.WHITE : new Color(200, 200, 200));
            line.setCentered(true);
            controlsLines.add(line);
        }

        backBtn = new TextButton("Back", new Vector2(0, 0.5f, 148, 0.5f), Vector2.ofOffset(200, 50));

        // Listeners
        createWorldBtn.addClickListener(btn -> { if (onCreateWorld != null) onCreateWorld.run(); });
        controlsBtn.addClickListener(btn -> showingControls = true);
        backBtn.addClickListener(btn -> showingControls = false);
    }

    /** Called when the user clicks "Create World". */
    public void setOnCreateWorld(Runnable callback) {
        this.onCreateWorld = callback;
    }

    @Override
    public void draw(Graphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible) return;

        if (overlay != null) overlay.draw(g2d, screenWidth, screenHeight);

        if (showingControls) {
            controlsBg.draw(g2d, screenWidth, screenHeight);
            
            // Ensure Graphics2D is flushed before GL drawing to avoid layering issues
            if (g2d instanceof javax.media.j3d.J3DGraphics2D) {
                ((javax.media.j3d.J3DGraphics2D) g2d).flush(false);
            }

            for (GuiText line : controlsLines) {
                line.draw(g2d, screenWidth, screenHeight);
            }
            backBtn.draw(g2d, screenWidth, screenHeight);
        } else {
            title.draw(g2d, screenWidth, screenHeight);
            createWorldBtn.draw(g2d, screenWidth, screenHeight);
            controlsBtn.draw(g2d, screenWidth, screenHeight);
        }
    }

    @Override
    public boolean handleMouseEvent(MouseEvent e, int screenWidth, int screenHeight) {
        if (!visible) return false;

        if (showingControls) {
            backBtn.handleMouseEvent(e, screenWidth, screenHeight);
        } else {
            createWorldBtn.handleMouseEvent(e, screenWidth, screenHeight);
            controlsBtn.handleMouseEvent(e, screenWidth, screenHeight);
        }

        return true; // Consume all events while menu is open
    }
}
