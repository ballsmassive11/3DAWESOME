package main;

import world.*;
import objects.Cube;
import javax.vecmath.Color3f;
import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        System.out.println("creating the world object");
        World world = new World(5);

        Cube cube = new Cube(1.0f);
        cube.setColor(new Color3f(0.2f, 0.6f, 1.0f));
        cube.setAngularVelocity(0.3, 0.8, 0.2); // radians/sec around X, Y, Z
        world.addObject(cube);

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