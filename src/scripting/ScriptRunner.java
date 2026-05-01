package scripting;

import org.python.util.PythonInterpreter;
import world.World;

public class ScriptRunner {
    private final PythonInterpreter interp;

    public ScriptRunner(World world) {
        interp = new PythonInterpreter();
        interp.set("game", new ModApi(world));
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
