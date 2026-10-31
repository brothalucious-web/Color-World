package com.colorworld.core;

/**
 * The color families ("groups") the world's colors are sorted into.
 *
 * <p>A group is a slice of the hue wheel. Every pixel whose original hue falls in a slice belongs to that
 * group, no matter what it is (grass, leaves, slime and a green wool block are all "green"). Because the
 * group is decided only by the ORIGINAL hue, a group always gets the same treatment everywhere in the
 * world, which is what keeps the groups consistent.
 *
 * <p>This class is plain Java (no Minecraft imports). The shader in
 * {@code color_world.fsh} repeats exactly the same hue math and boundaries.
 */
public final class ColorGroups {
    public static final int COUNT = 7;
    public static final String[] NAMES = {"red", "orange", "yellow", "green", "cyan", "blue", "purple"};

    /**
     * Pixels with less chroma than this are neutral (stone, snow, clouds, shadows). They carry no
     * "visual color", so they are ignored when measuring how much color has returned.
     */
    public static final float MIN_CHROMA = 0.04f;

    private ColorGroups() {
    }

    /**
     * Group index for a hue in degrees.
     * Slices: orange 15-40, yellow 40-75, green 75-140, cyan 140-190, blue 190-260, purple 260-320,
     * red 320-15 (wraps around 0). The edges sit in the gaps between Minecraft's big color clusters
     * (all grass/leaf tints are inside green, sky and water are both inside blue).
     */
    public static int groupOfHue(float hueDegrees) {
        float h = hueDegrees % 360f;
        if (h < 0f) {
            h += 360f;
        }
        if (h >= 15f && h < 40f) return 1;
        if (h >= 40f && h < 75f) return 2;
        if (h >= 75f && h < 140f) return 3;
        if (h >= 140f && h < 190f) return 4;
        if (h >= 190f && h < 260f) return 5;
        if (h >= 260f && h < 320f) return 6;
        return 0;
    }

    /** HSV chroma: the spread between the strongest and weakest channel, 0..1. */
    public static float chromaOf(float r, float g, float b) {
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    /** Hue in degrees [0, 360). Returns 0 for colors with no chroma. Same formula as the shader. */
    public static float hueOf(float r, float g, float b) {
        float mx = Math.max(r, Math.max(g, b));
        float chroma = mx - Math.min(r, Math.min(g, b));
        if (chroma <= 0f) {
            return 0f;
        }
        float h;
        if (mx == r) {
            h = (g - b) / chroma;
            if (h < 0f) {
                h += 6f;
            }
        } else if (mx == g) {
            h = (b - r) / chroma + 2f;
        } else {
            h = (r - g) / chroma + 4f;
        }
        return h * 60f;
    }
}
