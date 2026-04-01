package main;

import world.*;
import terrain.MapGeneratorLegacy;
import renderer.*;
import objects.MeshObject;
import javax.vecmath.Color3f;
import javax.swing.*;
import java.awt.*;


public class Main {

    public static void main(String[] args) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        System.out.println("creating the world object");
        World world = new World();

        MapGeneratorLegacy mapGen = new MapGeneratorLegacy();
        //mapGen.setGeneratorToNoise();
        int seed = (int) System.currentTimeMillis();
        mapGen.setSeed(seed);
        world.setSeed(seed);
        mapGen.generate(world);

        // Add the model object
        MeshObject suzanne = new MeshObject("src/resources/Suzanne/suzanne.obj");
        suzanne.setPosition(0.0f, 10.0f, -8.0f);
        suzanne.setScale(3.5f);
        suzanne.setColor(new Color3f(0.8f, 0.4f, 0.1f));
        suzanne.setAngularVelocity(0, 1.0, 0); // Rotate the monkey head
        world.addObject(suzanne);

        MeshObject boat = new MeshObject("src/resources/Boat/boat2.obj",true);
        boat.setPosition(-8.0f, 11.0f, -8.0f);
        boat.setScale(3.5f);
        boat.setAngularVelocity(0, 1.0, 0); // Rotate the monkey head
        world.addObject(boat);

        // Add the Ruger model object with textures
//        MeshObject ruger = new MeshObject("src/resources/Ruger/ruger.obj", true);
//        ruger.setPosition(-2.0f, 10.0f, 2.0f);
//        ruger.setScale(2.0f);
//        ruger.setAngularVelocity(0, 0.5, 0); // Rotate the gun
//        world.addObject(ruger);

        MeshObject rock = new MeshObject("src/resources/Rock/fuckassRock.obj", true);
        rock.setPosition(-8.0f, 30.0f, 8.0f);
        rock.setScale(12.0f);
        rock.setAngularVelocity(20, 35.5, -12); // Rotate the gun
        world.addObject(rock);

        System.out.println("creating the renderer object");
        Game3DRenderer renderer = new Game3DRenderer(world);

        JFrame frame = new JFrame("Ohio impressed");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true); // must be set before pack/show
        frame.add(renderer.getCanvas(), BorderLayout.CENTER);
        frame.pack();

        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(frame);
        } else {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        frame.setVisible(true);

        System.out.println("done");
    }
}