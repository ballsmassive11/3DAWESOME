package gui;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;

import javax.media.j3d.Canvas3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import java.awt.GraphicsConfiguration;
import java.nio.ByteBuffer;

/**
 * Secondary canvas used to render a minimap view and capture its pixels.
 */
public class MinimapCaptureCanvas extends Canvas3D {
    public static final int W = 320;
    public static final int H = 240;

    private final TransformGroup viewTG;
    private volatile Transform3D minimapTransform;
    private volatile Transform3D mainTransform;
    private final ByteBuffer pixels = ByteBuffer.allocateDirect(W * H * 3);
    private volatile boolean pixelsReady = false;

    public MinimapCaptureCanvas(GraphicsConfiguration config, TransformGroup viewTG) {
        super(config);
        this.viewTG = viewTG;
    }

    public void setTransforms(Transform3D minimapTransform, Transform3D mainTransform) {
        this.minimapTransform = new Transform3D(minimapTransform);
        this.mainTransform = new Transform3D(mainTransform);
    }

    @Override
    public void preRender() {
        Transform3D t = minimapTransform;
        if (t != null) {
            viewTG.setTransform(t);
        }
    }

    @Override
    public void postRender() {
        Transform3D t = mainTransform;
        if (t != null) {
            viewTG.setTransform(t);
        }

        try {
            GL2 gl = GLContext.getCurrent().getGL().getGL2();
            gl.glUseProgram(0);
            for (int i = 3; i >= 0; i--) {
                gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
                gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
                gl.glDisable(GL2.GL_TEXTURE_2D);
            }
            gl.glActiveTexture(GL2.GL_TEXTURE0);

            gl.glReadBuffer(GL2.GL_BACK);
            pixels.clear();
            gl.glPixelStorei(GL2.GL_PACK_ALIGNMENT, 1);
            gl.glReadPixels(0, 0, W, H, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, pixels);
            pixelsReady = true;
        } catch (Exception ignored) {
        }
    }

    public ByteBuffer getPixels() {
        return pixels;
    }

    public boolean isPixelsReady() {
        return pixelsReady;
    }

    public void clearPixelsReady() {
        pixelsReady = false;
    }
}
