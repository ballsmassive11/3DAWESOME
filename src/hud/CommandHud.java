package hud;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An in-game text input bar that the player can open, type into, and submit.
 * On submit the typed text is wrapped in a {@link CommandEvent} and delivered
 * to every registered listener, making it a general-purpose command bus.
 *
 * <p>While open, a history panel is rendered above the input bar showing
 * previous commands (white) and their responses (light blue).
 *
 * <h3>Controls</h3>
 * <ul>
 *   <li>{@code T} — open the input bar</li>
 *   <li>Any printable character — appended to the buffer</li>
 *   <li>{@code Backspace} — delete last character</li>
 *   <li>{@code Enter} — submit and close</li>
 *   <li>{@code Escape} — cancel and close without submitting</li>
 * </ul>
 */
public class CommandHud {

    /** Key that opens the input bar when it is currently closed. */
    public static final int TOGGLE_KEY = KeyEvent.VK_T;

    private static final int MAX_HISTORY = 60;
    private static final int VISIBLE_LINES = 12;

    // -----------------------------------------------------------------------
    // Log entry
    // -----------------------------------------------------------------------

    private enum EntryType { INPUT, OUTPUT }

    private static class LogEntry {
        final EntryType type;
        final String text;
        LogEntry(EntryType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private volatile boolean active = false;
    private volatile boolean suppressNextKeyTyped = false;
    private final StringBuilder inputBuffer = new StringBuilder();
    private final List<Consumer<CommandEvent>> listeners = new ArrayList<>();
    private final List<LogEntry> history = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public boolean isActive() { return active; }

    public void addListener(Consumer<CommandEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<CommandEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Write one or more response lines into the history panel.
     * Multi-line strings are split on {@code \n}.
     * Call this from command handlers to surface output to the player.
     */
    public void logOutput(String text) {
        for (String line : text.split("\n", -1)) {
            addEntry(new LogEntry(EntryType.OUTPUT, line));
        }
    }

    // -----------------------------------------------------------------------
    // Input routing
    // -----------------------------------------------------------------------

    public boolean handleToggle(int keyCode) {
        if (!active && keyCode == TOGGLE_KEY) {
            active = true;
            suppressNextKeyTyped = true;
            inputBuffer.setLength(0);
            return true;
        }
        return false;
    }

    public void keyTyped(char keyChar) {
        if (!active) return;
        if (suppressNextKeyTyped) {
            suppressNextKeyTyped = false;
            return;
        }
        if (keyChar == KeyEvent.CHAR_UNDEFINED) return;
        if (!Character.isISOControl(keyChar)) {
            inputBuffer.append(keyChar);
        }
    }

    public void keyPressed(int keyCode) {
        if (!active) return;
        switch (keyCode) {
            case KeyEvent.VK_ENTER:     submit(); break;
            case KeyEvent.VK_ESCAPE:    cancel(); break;
            case KeyEvent.VK_BACK_SPACE:
                if (inputBuffer.length() > 0)
                    inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                break;
            default: break;
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void submit() {
        String text = inputBuffer.toString().trim();
        if (!text.isEmpty()) {
            addEntry(new LogEntry(EntryType.INPUT, text));
            CommandEvent event = new CommandEvent(text);
            for (Consumer<CommandEvent> listener : listeners) {
                listener.accept(event);
            }
        }
        active = false;
        inputBuffer.setLength(0);
    }

    private void cancel() {
        active = false;
        inputBuffer.setLength(0);
    }

    private void addEntry(LogEntry entry) {
        history.add(entry);
        if (history.size() > MAX_HISTORY) history.remove(0);
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    public void draw(Graphics2D g2, int canvasWidth, int canvasHeight) {
        if (!active) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Font inputFont   = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        Font historyFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        Font hintFont    = new Font(Font.MONOSPACED, Font.BOLD,  11);

        g2.setFont(inputFont);
        FontMetrics ifm = g2.getFontMetrics();

        int pad  = 10;
        int barH = ifm.getHeight() + pad * 2;
        int barX = pad;
        int barW = canvasWidth - pad * 2;
        int barY = canvasHeight - barH - pad;

        // --- History panel ---------------------------------------------------
        if (!history.isEmpty()) {
            g2.setFont(historyFont);
            FontMetrics hfm = g2.getFontMetrics();
            int lineH = hfm.getHeight();

            int start = Math.max(0, history.size() - VISIBLE_LINES);
            List<LogEntry> visible = history.subList(start, history.size());

            int panelH = visible.size() * lineH + pad;
            int panelY = barY - panelH - 4;

            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(barX, panelY, barW, panelH, 8, 8);
            g2.setColor(new Color(80, 80, 120, 120));
            g2.drawRoundRect(barX, panelY, barW, panelH, 8, 8);

            int textX = barX + pad;
            int textY = panelY + pad / 2 + hfm.getAscent();
            for (LogEntry entry : visible) {
                if (entry.type == EntryType.INPUT) {
                    g2.setColor(new Color(220, 220, 220));
                    g2.drawString("> " + entry.text, textX, textY);
                } else {
                    g2.setColor(new Color(120, 190, 255));
                    g2.drawString("  " + entry.text, textX, textY);
                }
                textY += lineH;
            }
        }

        // --- Input bar -------------------------------------------------------
        g2.setFont(inputFont);

        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(barX, barY, barW, barH, 8, 8);
        g2.setColor(new Color(100, 160, 255, 200));
        g2.drawRoundRect(barX, barY, barW, barH, 8, 8);

        boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;
        String display = "> " + inputBuffer + (showCursor ? "|" : " ");
        g2.setColor(Color.WHITE);
        g2.drawString(display, barX + pad, barY + pad + ifm.getAscent());

        g2.setFont(hintFont);
        FontMetrics hntfm = g2.getFontMetrics();
        String hint = "ENTER submit  ESC cancel";
        g2.setColor(new Color(120, 170, 255, 160));
        g2.drawString(hint, barX + barW - hntfm.stringWidth(hint) - pad,
                barY + pad + ifm.getAscent());
    }
}
