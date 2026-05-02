package world;
import java.io.*;
import java.util.Properties;

/**
 * Global game quality and rendering settings, including persistence.
 */
public class GameSettings {
    private static final String SETTINGS_FILE = "game.properties";

    /** Current game quality level (1 = lowest, 10 = highest). Default: 5. */
    public static int quality = 5;

    /** Default mouse sensitivity. Default: 0.002. */
    public static double mouseSensitivity = 0.002;

    /** Default Field of View in radians. Default: PI/3 (~60 deg). */
    public static double fov = Math.PI / 3.0;

    /** Default render distance in world units. Default: 70.0. */
    public static double renderDistance = 70.0;

    /** Whether full-scene anti-aliasing (MSAA) is enabled. Default: true. */
    public static boolean antialiasing = true;

    static {
        load();
    }

    private GameSettings() {}

    public static void load() {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(SETTINGS_FILE)) {
            props.load(in);
            quality = Integer.parseInt(props.getProperty("quality", "5"));
            mouseSensitivity = Double.parseDouble(props.getProperty("mouseSensitivity", "0.002"));
            fov = Double.parseDouble(props.getProperty("fov", String.valueOf(Math.PI / 3.0)));
            renderDistance = Double.parseDouble(props.getProperty("renderDistance", "70.0"));
            antialiasing = Boolean.parseBoolean(props.getProperty("antialiasing", "true"));
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not load settings, using defaults: " + e.getMessage());
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("quality", String.valueOf(quality));
        props.setProperty("mouseSensitivity", String.valueOf(mouseSensitivity));
        props.setProperty("fov", String.valueOf(fov));
        props.setProperty("renderDistance", String.valueOf(renderDistance));
        props.setProperty("antialiasing", String.valueOf(antialiasing));

        try (OutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            props.store(out, "Game Settings");
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }
}
