package scripting;

import gui.canvas.GuiCanvas;
import org.python.util.PythonInterpreter;
import renderer.Game3DRenderer;
import world.World;

public class ScriptRunner {
    private final PythonInterpreter interp;

    public ScriptRunner(World world, GuiCanvas gui, Game3DRenderer renderer) {
        interp = new PythonInterpreter();
        interp.set("game", new ModApi(world, gui, renderer));
    }

    public String runFile(String path) {
        try {
            interp.execfile(path);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public String runString(String code) {
        try {
            interp.exec(code);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
