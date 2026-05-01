package gui.overlay;

import gui.components.GuiFrame;
import gui.components.TextButton;
import gui.core.GuiObject;
import gui.text.GuiText;
import gui.canvas.GuiCanvas;
import gui.vec.Vector2;

import java.awt.*;
import java.awt.event.MouseEvent;

public class PauseMenu extends GuiObject {

    private final GuiFrame backdrop;
    private final GuiText title;
    private final TextButton resumeBtn;
    private final TextButton quitBtn;

    private Runnable onResume;

    public PauseMenu() {
        backdrop = new GuiFrame(Vector2.ofScale(0.5f, 0.5f), Vector2.ofOffset(320, 260), new Color(15, 15, 15, 210));
        backdrop.setCentered(true);

        title = new GuiText(GuiCanvas.ARIAL, "PAUSED", new Vector2(0, 0.5f, -80, 0.5f));
        title.setPixelHeight(52f);
        title.setColor(Color.WHITE);
        title.setCentered(true);

        resumeBtn = new TextButton("Resume", new Vector2(0, 0.5f, 0, 0.5f), Vector2.ofOffset(240, 55));
        quitBtn   = new TextButton("Quit",   new Vector2(0, 0.5f, 75, 0.5f), Vector2.ofOffset(240, 55));

        resumeBtn.addClickListener(btn -> { if (onResume != null) onResume.run(); });
        quitBtn.addClickListener(btn -> System.exit(0));
    }

    public void setOnResume(Runnable callback) {
        this.onResume = callback;
    }

    @Override
    public void draw(Graphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible) return;
        backdrop.draw(g2d, screenWidth, screenHeight);
        title.draw(g2d, screenWidth, screenHeight);
        resumeBtn.draw(g2d, screenWidth, screenHeight);
        quitBtn.draw(g2d, screenWidth, screenHeight);
    }

    @Override
    public boolean handleMouseEvent(MouseEvent e, int screenWidth, int screenHeight) {
        if (!visible) return false;
        resumeBtn.handleMouseEvent(e, screenWidth, screenHeight);
        quitBtn.handleMouseEvent(e, screenWidth, screenHeight);
        return true;
    }
}
