package main;

import world.*;
import terrain.MapGenerator;
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

        MapGenerator mapGen = new MapGenerator();
        //mapGen.setGeneratorToNoise();
        mapGen.generate(world);

        // Add the model object
        MeshObject suzanne = new MeshObject("src/resources/Suzanne/suzanne.obj");
        suzanne.setPosition(0.0f, 10.0f, -8.0f);
        suzanne.setScale(3.5f);
        suzanne.setColor(new Color3f(0.8f, 0.4f, 0.1f));
        suzanne.setAngularVelocity(0, 1.0, 0); // Rotate the monkey head
        world.addObject(suzanne);

        MeshObject boat = new MeshObject("src/resources/Boat/boat2.obj",true);
        boat.setPosition(0.0f, 19.0f, -8.0f);
        boat.setScale(3.5f);
        boat.setAngularVelocity(0, 1.0, 0); // Rotate the monkey head
        world.addObject(boat);

        // Add the Ruger model object with textures
        MeshObject ruger = new MeshObject("src/resources/Ruger/ruger.obj", true);
        ruger.setPosition(-2.0f, 10.0f, 2.0f);
        ruger.setScale(2.0f);
        ruger.setAngularVelocity(0, 0.5, 0); // Rotate the gun
        world.addObject(ruger);

        MeshObject table2 = new MeshObject("src/resources/Table2/table2.obj", true);
        table2.setPosition(-8.0f, 10.0f, -3.0f);
        table2.setScale(2.0f);
        table2.setAngularVelocity(0, 0.5, 0); // Rotate the gun
        world.addObject(table2);

        MeshObject rock = new MeshObject("src/resources/Rock/fuckassRock.obj", true);
        rock.setPosition(-8.0f, 10.0f, 8.0f);
        rock.setScale(2.0f);
        rock.setAngularVelocity(0, 0.5, 0); // Rotate the gun
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