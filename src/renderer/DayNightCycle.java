package renderer;

import javax.vecmath.Color3f;
import javax.vecmath.Vector3f;

/**
 * Tracks the current time-of-day and provides interpolated sky/lighting colors.
 *
 * timeOfDay: 0.0 = midnight, 0.25 = sunrise, 0.5 = noon, 0.75 = sunset, 1.0 = midnight (wraps)
 */
public class DayNightCycle {

    private double timeOfDay = 0.3; // start shortly after sunrise
    private double cycleDuration = 160.0; // seconds for one full day
    private boolean paused = false;

    // --- Day colors (noon) ---
    private static final Color3f DAY_AMBIENT = new Color3f(0.85f, 0.85f, 0.85f);
    private static final Color3f DAY_SUN     = new Color3f(1.0f,  1.0f,  1.0f);
    private static final Color3f DAY_FOG     = new Color3f(0.9f,  0.9f,  0.9f);

    // --- Night colors (midnight) — moonlight: cool silver-blue, bright enough for water specular ---
    private static final Color3f NIGHT_AMBIENT = new Color3f(0.08f, 0.08f, 0.21f);
    private static final Color3f NIGHT_SUN     = new Color3f(0.22f, 0.24f, 0.38f);
    private static final Color3f NIGHT_FOG     = new Color3f(0.03f, 0.03f, 0.10f);

    // --- Dawn / dusk tint colors ---
    private static final Color3f DAWN_AMBIENT = new Color3f(0.75f, 0.55f, 0.35f);
    private static final Color3f DAWN_SUN     = new Color3f(1.0f,  0.60f, 0.20f);
    private static final Color3f DAWN_FOG     = new Color3f(0.85f, 0.58f, 0.42f);

    public void update(double dt) {
        if (!paused) {
            timeOfDay = (timeOfDay + dt / cycleDuration) % 1.0;
        }
    }

    /**
     * Returns 0 at night, 1 at full noon, using a smooth sin curve.
     * Dawn: 0.25, Noon: 0.5, Dusk: 0.75
     */
    public double getDaylightFactor() {
        // sin goes from -1 at midnight (t=0) to +1 at noon (t=0.5)
        double angle = (timeOfDay - 0.25) * 2.0 * Math.PI;
        return Math.max(0.0, Math.sin(angle));
    }

    /** True when the sun is above the horizon. */
    public boolean isDay() {
        return timeOfDay > 0.22 && timeOfDay < 0.78;
    }

    /** Ambient light color, interpolated across day/night/dawn. */
    public Color3f getAmbientColor() {
        double day  = getDaylightFactor();
        double dawn = getDawnFactor();
        Color3f base = lerp(NIGHT_AMBIENT, DAY_AMBIENT, day);
        return lerp(base, DAWN_AMBIENT, dawn * 0.5);
    }

    /** Directional (sun) light color, interpolated across day/night/dawn. */
    public Color3f getSunColor() {
        double day  = getDaylightFactor();
        double dawn = getDawnFactor();
        Color3f base = lerp(NIGHT_SUN, DAY_SUN, day);
        return lerp(base, DAWN_SUN, dawn * 0.7);
    }

    /** Fog color for the LinearFog in the scene. */
    public Color3f getFogColor() {
        double day  = getDaylightFactor();
        double dawn = getDawnFactor();
        Color3f base = lerp(NIGHT_FOG, DAY_FOG, day);
        return lerp(base, DAWN_FOG, dawn * 0.7);
    }

    /** Gaussian peak at dawn (0.25) and dusk (0.75), zero at noon and midnight. */
    private double getDawnFactor() {
        double dawnPeak = gaussianPeak(timeOfDay, 0.25, 0.04);
        double duskPeak = gaussianPeak(timeOfDay, 0.75, 0.04);
        return Math.min(1.0, dawnPeak + duskPeak);
    }

    private static double gaussianPeak(double t, double center, double sigma) {
        double d = t - center;
        return Math.exp(-d * d / (2.0 * sigma * sigma));
    }

    private static Color3f lerp(Color3f a, Color3f b, double t) {
        float ft = (float) Math.max(0.0, Math.min(1.0, t));
        return new Color3f(
                a.x + (b.x - a.x) * ft,
                a.y + (b.y - a.y) * ft,
                a.z + (b.z - a.z) * ft);
    }

    /**
     * Direction the sun/moon light travels (FROM celestial body TOWARD the scene).
     * The sun arcs from east (+X horizon) at dawn, through directly overhead at noon,
     * to west (-X horizon) at dusk, and below the world at midnight.
     * A small southward tilt (-Z) gives a northern-hemisphere feel.
     */
    public Vector3f getSunDirection() {
        double angle = (timeOfDay - 0.25) * 2.0 * Math.PI;
        float x = -(float) Math.cos(angle);
        float y = -(float) Math.sin(angle);

        // If the light direction points upward (y > 0), it means the sun is below the horizon.
        // In this case, we treat the light as moonlight coming from the opposite side.
        if (y > 0) {
            x = -x;
            y = -y;
        }

        Vector3f dir = new Vector3f(x, y, -0.15f);
        dir.normalize();
        return dir;
    }

    // --- Getters / setters ---

    public double getTimeOfDay()                  { return timeOfDay; }
    public void   setTimeOfDay(double t)          { timeOfDay = ((t % 1.0) + 1.0) % 1.0; }
    public double getCycleDuration()              { return cycleDuration; }
    public void   setCycleDuration(double secs)   { cycleDuration = Math.max(1.0, secs); }
    public boolean isPaused()                     { return paused; }
    public void    setPaused(boolean p)           { paused = p; }
}
