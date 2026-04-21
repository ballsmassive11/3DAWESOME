package world;

/**
 * Global game quality and rendering settings.
 *
 * <p>{@code quality} ranges from 1 (lowest) to 10 (highest) and controls
 * which rendering features are active.  Individual systems check this value
 * against their own thresholds to decide whether to skip expensive work.
 */
public class GameSettings {

    /** Current game quality level (1 = lowest, 10 = highest). Default: 5. */
    public static int quality = 5;

    private GameSettings() {}
}
