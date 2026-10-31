package com.colorworld.client;

import com.colorworld.ColorWorldMod;
import com.colorworld.core.ChromaHistogram;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Takes a small sample of the rendered picture so the game can tell how much color is on screen.
 *
 * <p>The main framebuffer is shrunk into a tiny 128x72 render target and read back (about 37 KB), which is
 * cheap enough to do ten times a second. It only uses a renderbuffer, so it never touches the texture
 * bindings Minecraft keeps track of, and it puts every piece of GL state it changes back where it was.
 */
final class FrameSampler {
    private static final int W = 128;
    private static final int H = 72;

    private final ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
    private int fbo;
    private int rbo;
    private boolean broken;

    /** Returns null if sampling is not possible (it then stays off for the rest of the session). */
    ChromaHistogram sample(Framebuffer source) {
        if (broken || source.textureWidth <= 0 || source.textureHeight <= 0) {
            return null;
        }
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            if (fbo == 0 && !init()) {
                broken = true;
                ColorWorldMod.LOGGER.warn("Color World: could not create the sampling target; the death meter is disabled");
                return null;
            }
            if (scissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
            GL30.glBlitFramebuffer(0, 0, source.textureWidth, source.textureHeight,
                    0, 0, W, H, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            pixels.clear();
            GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return ChromaHistogram.fromRgba8(pixels, W * H);
        } catch (Throwable t) {
            broken = true;
            ColorWorldMod.LOGGER.warn("Color World: sampling the screen failed; the death meter is disabled", t);
            return null;
        } finally {
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        }
    }

    private boolean init() {
        fbo = GL30.glGenFramebuffers();
        rbo = GL30.glGenRenderbuffers();

        int prevRbo = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rbo);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, W, H);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRbo);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, rbo);
        return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }
}
