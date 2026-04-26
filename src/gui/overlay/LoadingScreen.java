package gui.overlay;

import gui.canvas.GuiCanvas;
import gui.components.GuiFrame;
import gui.components.GuiTexture;
import gui.core.GuiObject;
import gui.text.GuiText;
import gui.vec.Vector2;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * A simple loading screen overlay with a solid background and text.
 */
public class LoadingScreen extends GuiObject {

    private final GuiFrame background;
    private final GuiText loadingText;
    private final GuiText statusText;
    private final GuiFrame progressBarBg;
    private final GuiFrame progressBarFill;
    private final GuiTexture face;

    private float progress = 0f;
    private String status = "Initialising...";

    private Thread animationThread;
    private boolean animating = false;

    public LoadingScreen() {
        // Full screen dark background
        background = new GuiFrame(Vector2.ofScale(0.5f, 0.5f), Vector2.ofScale(1.0f, 1.0f), new Color(15, 15, 15));
        background.setCentered(true);

        face = new GuiTexture("/gui/scaredsre.png");
        face.setCentered(true);
        face.setPosition(new Vector2(-200f,1f,0f, 0.5f));
        face.setSize(Vector2.ofOffset(250f, 250));
        face.setVisible(true);

        // Loading text in the center (slightly above middle)
        loadingText = new GuiText(GuiCanvas.ARIAL, "LOADING WORLD...", Vector2.ofScale(0.5f, 0.45f));
        loadingText.setPixelHeight(48f);
        loadingText.setColor(Color.WHITE);
        loadingText.setCentered(true);

        // Status text below the progress bar
        statusText = new GuiText(GuiCanvas.ARIAL, status, Vector2.ofScale(0.5f, 0.65f));
        statusText.setPixelHeight(24f);
        statusText.setColor(new Color(200, 200, 200));
        statusText.setCentered(true);

        // Progress bar background
        progressBarBg = new GuiFrame(Vector2.ofScale(0.5f, 0.55f), Vector2.ofOffset(600, 30), new Color(50, 50, 50));
        progressBarBg.setCentered(true);
        progressBarBg.setBorderWidth(2f);
        progressBarBg.setBorderColor(new Color(100, 100, 100));

        // Progress bar fill
        progressBarFill = new GuiFrame(Vector2.ofScale(0.5f, 0.55f), Vector2.ofOffset(0, 26), new Color(0, 150, 255));
        progressBarFill.setCentered(true);
        
        visible = false;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private void startAnimation() {
        if (animating) return;
        animating = true;
        animationThread = new Thread(() -> {
            long lastTime = System.currentTimeMillis();
            while (animating) {
                long currentTime = System.currentTimeMillis();
                double deltaTime = (currentTime - lastTime) / 1000.0;
                lastTime = currentTime;

                double currentRotation = face.getRotation();
                face.setRotation(currentRotation + deltaTime * Math.PI * 2); // 1 rotation per second

                try {
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        animationThread.setDaemon(true);
        animationThread.setName("LoadingScreen-Animation");
        animationThread.start();
    }

    private void stopAnimation() {
        animating = false;
        if (animationThread != null) {
            animationThread.interrupt();
            animationThread = null;
        }
    }

    public void setProgress(float progress, String status) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        if (status != null) {
            this.status = status;
            this.statusText.setText(status);
        }
        
        // Update fill width based on progress
        int maxWidth = 596; // 600 - 2*border
        progressBarFill.setSize(Vector2.ofOffset((int)(maxWidth * this.progress), 26));
        
        // Let's make it not centered for easier alignment
        progressBarFill.setCentered(false);
        // Vector2 base is 0.5f, 0.55f. We want to offset by -300+2 pixels in X and -13 pixels in Y
        progressBarFill.setPosition(new Vector2(-300 + 2, 0.5f, -13, 0.55f));
    }

    @Override
    public void draw(Graphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible) return;

        background.draw(g2d, screenWidth, screenHeight);
        progressBarBg.draw(g2d, screenWidth, screenHeight);
        face.draw(g2d, screenWidth, screenHeight);
        
        if (progress > 0) {
            progressBarFill.draw(g2d, screenWidth, screenHeight);
        }

        // Ensure Graphics2D is flushed for GL text if needed
        if (g2d instanceof javax.media.j3d.J3DGraphics2D) {
            ((javax.media.j3d.J3DGraphics2D) g2d).flush(false);
        }
        
        loadingText.draw(g2d, screenWidth, screenHeight);
        statusText.draw(g2d, screenWidth, screenHeight);
    }

    @Override
    public boolean handleMouseEvent(MouseEvent e, int screenWidth, int screenHeight) {
        // Consume all mouse events while loading to prevent clicking things behind
        return visible;
    }
}
