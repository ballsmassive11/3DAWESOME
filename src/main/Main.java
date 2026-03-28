package main;

import world.*;
import renderer.*;
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

        new MapGenerator().generate(world);

        // Add the model object
        ModelObject suzanne = new ModelObject("src/resources/Suzanne/suzanne.obj");
        suzanne.setPosition(0.0f, 0.0f, -5.0f);
        suzanne.setScale(1.5f);
        suzanne.setColor(new Color3f(0.8f, 0.4f, 0.1f));
        suzanne.setAngularVelocity(0, 1.0, 0); // Rotate the monkey head
        world.addObject(suzanne);

        // Add the Ruger model object with textures
        ModelObject ruger = new ModelObject("src/resources/Ruger/ruger.obj", true);
        ruger.setPosition(-2.0f, 0.0f, -5.0f);
        ruger.setScale(1.0f);
        ruger.setAngularVelocity(0, 0.5, 0); // Rotate the gun
        world.addObject(ruger);

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