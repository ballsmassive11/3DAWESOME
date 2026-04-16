package world;

/**
 * Global game quality and rendering settings.
 *
 * <p>{@code quality} ranges from 1 (lowest) to 10 (highest) and controls
 * which rendering features are active.  Individual systems check this value
 * against their own thresholds to decide whether to skip expensive work.
 *
 * <h3>Water reflection quality</h3>
 * <ul>
 *   <li>quality &lt; {@link #WATER_RENDER_THRESHOLD}: RTT passes are skipped;
 *       water shows a flat placeholder colour.</li>
 *   <li>quality &ge; {@link #WATER_RENDER_THRESHOLD}: full reflection + refraction RTT.</li>
 * </ul>
 */
public class GameSettings {

    /** Current game quality level (1 = lowest, 10 = highest). Default: 5. */
    public static int quality = 5;

    /**
     * Minimum quality level required to render water reflections/refractions.
     * Below this threshold the RTT passes are skipped entirely.
     */
    public static final int WATER_RENDER_THRESHOLD = 3;

    private GameSettings() {}
}
