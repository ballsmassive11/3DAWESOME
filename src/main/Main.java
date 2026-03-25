package main;

import world.*;
import renderer.*;
import objects.OscillatingCube;
import objects.ModelObject;
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
        cube.setPosition(2.0f, 0.0f, -5.0f);
        cube.setColor(new Color3f(0.2f, 0.6f, 1.0f));
        world.addObject(cube);
        
        // Add the model object
        ModelObject suzanne = new ModelObject("src/resources/suzanne.obj");
        suzanne.setPosition(0.0f, 0.0f, -5.0f);
        suzanne.setScale(1.5f);
        suzanne.setColor(new Color3f(0.8f, 0.4f, 0.1f));
        suzanne.setAngularVelocity(0, 1.0, 0); // Rotate the monkey head
        world.addObject(suzanne);

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