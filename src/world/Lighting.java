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
        ambientLight = new AmbientLight(new Color3f(0.45f, 0.45f, 0.45f));
        ambientLight.setCapability(AmbientLight.ALLOW_COLOR_WRITE);
        ambientLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));

        // Directional light
        directionalLight = new DirectionalLight(
            new Color3f(1.0f, 1.0f, 1.0f),
            new Vector3f(-1.0f, -1.0f, -1.0f)
        );
        directionalLight.setCapability(DirectionalLight.ALLOW_COLOR_WRITE);
        directionalLight.setCapability(DirectionalLight.ALLOW_DIRECTION_WRITE);
        directionalLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
    }

    /**
     * Add the lights to the specified scene branch group
     * @param sceneBranchGroup the branch group to add lights to
     */
    public void addToScene(BranchGroup sceneBranchGroup) {
        sceneBranchGroup.addChild(ambientLight);
        sceneBranchGroup.addChild(directionalLight);
    }

    public void setAmbientColor(Color3f color) {
        ambientLight.setColor(color);
    }

    public void setDirectionalColor(Color3f color) {
        directionalLight.setColor(color);
    }

    public void setDirectionalDirection(Vector3f dir) {
        directionalLight.setDirection(dir);
    }

    public AmbientLight getAmbientLight() {
        return ambientLight;
    }

    public DirectionalLight getDirectionalLight() {
        return directionalLight;
    }
}
