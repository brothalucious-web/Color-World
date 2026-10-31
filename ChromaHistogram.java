package com.colorworld.core;

import java.nio.ByteBuffer;

/**
 * How much color each group contributes to a picture, measured as total chroma.
 * "Visual color" is therefore literally the amount of color you can see, so a huge green hillside weighs far
 * more than one small red flower, and gray stone weighs nothing.
 */
public final class ChromaHistogram {
    public final float[] perGroup = new float[ColorGroups.COUNT];
    public float total;
    public int pixels;

    /** Reads {@code pixelCount} RGBA8 pixels from the start of the buffer (absolute reads, position untouched). */
    public static ChromaHistogram fromRgba8(ByteBuffer rgba, int pixelCount) {
        ChromaHistogram h = new ChromaHistogram();
        h.pixels = pixelCount;
        for (int i = 0; i < pixelCount; i++) {
            int o = i * 4;
            h.add((rgba.get(o) & 0xFF) / 255f, (rgba.get(o + 1) & 0xFF) / 255f, (rgba.get(o + 2) & 0xFF) / 255f);
        }
        return h;
    }

    public void add(float r, float g, float b) {
        float chroma = ColorGroups.chromaOf(r, g, b);
        if (chroma < ColorGroups.MIN_CHROMA) {
            return;
        }
        int group = ColorGroups.groupOfHue(ColorGroups.hueOf(r, g, b));
        perGroup[group] += chroma;
        total += chroma;
    }

    /** Average chroma per pixel: how colorful the picture is overall (0 = pure grayscale). */
    public float coverage() {
        return pixels > 0 ? total / pixels : 0f;
    }
}
