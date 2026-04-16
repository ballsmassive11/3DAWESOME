package gui;

import com.jogamp.opengl.GL2;

import java.nio.ByteBuffer;

/**
 * Owns an FBO with color and depth attachments and draws its texture on-screen.
 */
public class MinimapFboOverlay {
    private static final int PX = 10;
    private static final int PY = 10;
    private static final int PW = MinimapCaptureCanvas.W;
    private static final int PH = MinimapCaptureCanvas.H;

    private int fboId = -1;
    private int texId = -1;
    private int rboId = -1;
    private boolean initialized = false;

    public void render(GL2 gl, int screenW, int screenH, MinimapCaptureCanvas minimapCanvas) {
        if (!initialized) {
            init(gl);
        }

        if (minimapCanvas != null && minimapCanvas.isPixelsReady()) {
            ByteBuffer pixels = minimapCanvas.getPixels();
            pixels.rewind();
            gl.glBindTexture(GL2.GL_TEXTURE_2D, texId);
            gl.glTexSubImage2D(GL2.GL_TEXTURE_2D, 0, 0, 0,
                    MinimapCaptureCanvas.W, MinimapCaptureCanvas.H,
                    GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, pixels);
            gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
            minimapCanvas.clearPixelsReady();
        }

        drawQuad(gl, screenW, screenH);
    }

    private void init(GL2 gl) {
        int[] id = new int[1];

        gl.glGenTextures(1, id, 0);
        texId = id[0];
        gl.glBindTexture(GL2.GL_TEXTURE_2D, texId);
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGB8,
                MinimapCaptureCanvas.W, MinimapCaptureCanvas.H,
                0, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, null);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);

        gl.glGenRenderbuffers(1, id, 0);
        rboId = id[0];
        gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, rboId);
        gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT24,
                MinimapCaptureCanvas.W, MinimapCaptureCanvas.H);
        gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, 0);

        gl.glGenFramebuffers(1, id, 0);
        fboId = id[0];
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fboId);
        gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0,
                GL2.GL_TEXTURE_2D, texId, 0);
        gl.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT,
                GL2.GL_RENDERBUFFER, rboId);
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);

        initialized = true;
    }

    private void drawQuad(GL2 gl, int screenW, int screenH) {
        gl.glPushAttrib(GL2.GL_ALL_ATTRIB_BITS);

        gl.glViewport(PX, screenH - PY - PH, PW, PH);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, texId);

        gl.glColor4f(1f, 1f, 1f, 1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glTexCoord2f(0f, 0f); gl.glVertex2f(-1f, -1f);
        gl.glTexCoord2f(1f, 0f); gl.glVertex2f( 1f, -1f);
        gl.glTexCoord2f(1f, 1f); gl.glVertex2f( 1f,  1f);
        gl.glTexCoord2f(0f, 1f); gl.glVertex2f(-1f,  1f);
        gl.glEnd();

        gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopMatrix();
        gl.glPopAttrib();

        gl.glViewport(0, 0, screenW, screenH);
    }
}
