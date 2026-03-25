package main;

import world.*;
import objects.OscillatingCube;
import javax.vecmath.Color3f;
import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        System.out.println("creating the world object");
        World world = new World();

        OscillatingCube cube = new OscillatingCube(1.0f);
        cube.setPosition(0.0f, 0.0f, -5.0f);
        cube.setColor(new Color3f(0.2f, 0.6f, 1.0f));
        world.addObject(cube);
        
        // Add some more cubes to fly around and see
        for (int i = 0; i < 5; i++) {
            OscillatingCube extra = new OscillatingCube(0.5f);
            extra.setPosition(Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 15);
            extra.setColor(new Color3f((float)Math.random(), (float)Math.random(), (float)Math.random()));
            world.addObject(extra);
        }

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