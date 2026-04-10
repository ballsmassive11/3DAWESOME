package gui;

/**
 * Fired when the player submits text via the command HUD input bar.
 * Other systems subscribe to CommandHud to receive these events.
 */
public class CommandEvent {
    private final String text;
    private final long timestamp;

    public CommandEvent(String text) {
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    /** The raw text the player typed. */
    public String getText() { return text; }

    /** Millisecond timestamp of when the command was submitted. */
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "CommandEvent{text='" + text + "'}";
    }
}
