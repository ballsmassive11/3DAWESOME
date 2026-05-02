package main;

import gui.canvas.GuiCanvas;
import gui.components.GuiFrame;
import gui.components.GuiTexture;
import gui.components.TextButton;
import gui.core.*;
import gui.overlay.*;
import gui.text.GuiText;
import gui.vec.Vector2;
import objects.*;
import particles.ParticleEmitter;
import scripting.ScriptRunner;
import world.*;
import terrain.MapGenerator;
import renderer.*;
import javax.media.j3d.Appearance;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;
import javax.vecmath.Color4f;
import javax.swing.*;
import java.awt.*;
import java.io.File;


public class Main {
    private static boolean worldCreated = false;
    private static MeshObject menuSuzanne;

    public static void main(String[] args) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        System.out.println("creating the world object");
        World world = new World();

        System.out.println("creating the renderer object");
        Game3DRenderer renderer = new Game3DRenderer(world);

        final Canvas3D mainCanvas = renderer.getCanvas();
        GuiCanvas gui = renderer.getGuiCanvas();

        JFrame frame = new JFrame("Ohio impressed");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.add(mainCanvas);
        frame.pack();

        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(frame);
        } else {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        frame.setVisible(true);

        // Position camera for menu
        world.getCamera().setPosition(0, 10, 5); // Position camera slightly back (+Z)
        world.getCamera().setRotationDisabled(true);
        world.setPhysicsEnabled(false);

        // Ensure lighting is active for the menu
        renderer.notifySceneReady();
        renderer.setMenuActive(true);

        // Spawn Suzanne for menu
        menuSuzanne = new MeshObject("resources/models/Suzanne/suzanne.obj");
        menuSuzanne.setPosition(0, 10, -5); // Suzanne remains at -5
        menuSuzanne.setScale(3.5); // Larger scale to match the one in createWorldContent
        menuSuzanne.setColor(new Color3f(1.0f, 0.7f, 0.2f)); // Bright gold-ish color
        menuSuzanne.setAngularVelocity(0, 1.5, 0);
        world.addObject(menuSuzanne);

        // Show main menu immediately
        MainMenu mainMenu = new MainMenu();
        mainMenu.setOnCreateWorld(() -> {
            if (worldCreated) return;
            worldCreated = true;
            
            // Immediately show loading screen
            gui.showLoadingScreen();
            
            // Run generation in a background thread to keep the loading screen rendering
            new Thread(() -> {
                try {
                    // Start removing menu elements from the background thread might be risky, 
                    // but guiObjects is thread-safe (CopyOnWriteArrayList).
                    mainMenu.setVisible(false);
                    gui.removeObject(mainMenu);
                    
                    // Cleanup menu model
                    if (menuSuzanne != null) {
                        world.removeObject(menuSuzanne);
                        menuSuzanne = null;
                    }
                    world.getCamera().setRotationDisabled(false);
                    renderer.setMenuActive(false);
                    world.setPhysicsEnabled(true);
                    
                    // Generate world content (heavy task)
                    gui.setLoadingProgress(0.05f, "Preparing world...");
                    createWorldContent(world, renderer);
                    
                    gui.setLoadingProgress(1.0f, "Done!");
                    
                    // Once done, switch back to EDT to hide loading screen and show HUD
                    SwingUtilities.invokeLater(() -> {
                        gui.hideLoadingScreen();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });
        gui.addObject(mainMenu);

        System.out.println("Main menu loaded");
    }

    private static void createWorldContent(World world, Game3DRenderer renderer) {
        GuiCanvas gui = renderer.getGuiCanvas();
        System.out.println("Generating world content...");
        renderer.notifySceneChanging();

        // Set the player's visible body model
        gui.setLoadingProgress(0.05f, "Loading player model...");
        world.setPlayerModel("resources/models/Guy/guy.obj");

        // Run any mods/*.py — scripts may override the default model and movespeed
        File modsDir = new File("mods");
        if (modsDir.isDirectory()) {
            File[] pys = modsDir.listFiles((d, n) -> n.endsWith(".py"));
            if (pys != null && pys.length > 0) {
                ScriptRunner runner = new ScriptRunner(world, gui, renderer);
                for (File f : pys) {
                    String err = runner.runFile(f.getPath());
                    if (err != null) System.err.println("[mod " + f.getName() + "] " + err);
                }
            }
        }

        gui.setLoadingProgress(0.1f, "Initializing map generator...");
        MapGenerator mapGen = new MapGenerator();
        mapGen.setReporter((p, s) -> gui.setLoadingProgress(0.1f + p * 0.6f, s));
        int seed = (int) System.currentTimeMillis();
        mapGen.setSeed(seed);
        world.setSeed(seed);
        
        mapGen.generate(world);

        gui.setLoadingProgress(0.75f, "Spawning world objects...");



        gui.setLoadingProgress(0.9f, "Setting up GUI...");

        TextButton spawnSuzanne = new TextButton("Spawn Suzanne", Vector2.ofScale(0.15f, 0.9f), Vector2.ofOffset(200, 50));
        spawnSuzanne.addClickListener(btn -> {
            MeshObject newSuzanne = new MeshObject("resources/models/Suzanne/suzanne.obj");
            newSuzanne.setPosition((float) (Math.random() * 20 - 10), 15.0f, (float) (Math.random() * 20 - 10));
            newSuzanne.setScale(2.5f);
            newSuzanne.setColor(new Color3f((float) Math.random(), (float) Math.random(), (float) Math.random()));
            newSuzanne.setAngularVelocity(0, Math.random() * 2, 0);
            world.addObject(newSuzanne);
        });
        gui.addObject(spawnSuzanne);

        gui.setLoadingProgress(0.95f, "Finalizing scene...");
        renderer.notifySceneReady();
        System.out.println("World content generated.");
    }
}