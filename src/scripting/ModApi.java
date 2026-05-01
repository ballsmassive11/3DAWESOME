package scripting;

import world.World;

/**
 * Surface exposed to Python mod scripts as the global `game`.
 * Keep methods small and stable — scripts will depend on these names.
 */
public class ModApi {
    private final World world;

    public ModApi(World world) {
        this.world = world;
    }

    public void setPlayerModel(String path) {
        world.setPlayerModel(path);
    }

    public void setMoveSpeed(double speed) {
        world.getCamera().setMoveSpeed(speed);
    }
}
