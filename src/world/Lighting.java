package world;

import javax.media.j3d.*;
import javax.vecmath.*;

/**
 * Handles the lighting aspects of the renderer
 */
public class Lighting {
    private AmbientLight ambientLight;
    private DirectionalLight directionalLight;

    public Lighting() {
        setupLighting();
    }

    /**
     * Setup default lighting for the scene
     */
    private void setupLighting() {
        // Ambient light
        ambientLight = new AmbientLight(new Color3f(0.6f, 0.6f, 0.6f));
        ambientLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), 100.0));

        // Directional light
        directionalLight = new DirectionalLight(
            new Color3f(1.0f, 1.0f, 1.0f),
            new Vector3f(-1.0f, -1.0f, -1.0f)
        );
        directionalLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), 100.0));
    }

    /**
     * Add the lights to the specified scene branch group
     * @param sceneBranchGroup the branch group to add lights to
     */
    public void addToScene(BranchGroup sceneBranchGroup) {
        sceneBranchGroup.addChild(ambientLight);
        sceneBranchGroup.addChild(directionalLight);
    }

    public AmbientLight getAmbientLight() {
        return ambientLight;
    }

    public DirectionalLight getDirectionalLight() {
        return directionalLight;
    }
}
