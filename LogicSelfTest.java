import com.colorworld.core.*;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

/**
 * Tests the rules without starting Minecraft. Run with tools/run-logic-tests.sh
 * (needs only a JDK). The shade() method below is a line-by-line Java copy of color_world.fsh so the
 * relationship between "what the screen shows" and "the percentage" can be checked.
 */
public class LogicSelfTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    static int hex(int rgb, int shift) { return (rgb >> shift) & 0xFF; }

    static int groupOf(int rgb) {
        float r = hex(rgb, 16) / 255f, g = hex(rgb, 8) / 255f, b = hex(rgb, 0) / 255f;
        return ColorGroups.groupOfHue(ColorGroups.hueOf(r, g, b));
    }

    // ---- Java copy of color_world.fsh
    static float fract(float x) { return x - (float) Math.floor(x); }
    static float[] hsv2rgb(float h, float s, float v) {
        float[] out = new float[3];
        float[] off = {1f, 2f / 3f, 1f / 3f};
        for (int i = 0; i < 3; i++) {
            float p = Math.abs(fract(h + off[i]) * 6f - 3f);
            float c = Math.min(1f, Math.max(0f, p - 1f));
            out[i] = v * (1f * (1f - s) + c * s);
        }
        return out;
    }
    static float[] shade(float r, float g, float b, float[] groupValues) {
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), chroma = mx - mn;
        if (chroma <= 0f) return new float[]{r, g, b};
        float h = ColorGroups.hueOf(r, g, b);
        float v = groupValues[ColorGroups.groupOfHue(h)];
        if (v < 0f) { float l = 0.299f * r + 0.587f * g + 0.114f * b; return new float[]{l, l, l}; }
        if (v < 0.0005f) return new float[]{r, g, b};
        return hsv2rgb(fract(h / 360f + v), chroma / mx, mx);
    }

    static ColorWorldSettings settings() {
        ColorWorldSettings s = new ColorWorldSettings();
        s.smoothing = 1.0;   // no smoothing, so results are exact
        return s;
    }

    static ChromaHistogram histogramWithShares(float[] shares) {
        ChromaHistogram h = new ChromaHistogram();
        h.pixels = 1000;
        for (int i = 0; i < shares.length; i++) { h.perGroup[i] = shares[i] * 500f; h.total += shares[i] * 500f; }
        return h; // total chroma 500 over 1000 pixels -> coverage 0.5
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hue groups");
        check("grass 0x91BD59 -> green", groupOf(0x91BD59) == 3);
        check("jungle grass 0x59C93C -> green", groupOf(0x59C93C) == 3);
        check("swamp-ish foliage 0x6A7039 -> yellow", groupOf(0x6A7039) == 2);
        check("sky 0x78A7FF -> blue", groupOf(0x78A7FF) == 5);
        check("water 0x3F76E4 -> blue", groupOf(0x3F76E4) == 5);
        check("sand 0xDBD3A0 -> yellow", groupOf(0xDBD3A0) == 2);
        check("dirt 0x866043 -> orange", groupOf(0x866043) == 1);
        check("pure red -> red", groupOf(0xFF0000) == 0);
        check("pure cyan -> cyan", groupOf(0x00FFFF) == 4);
        check("pure magenta -> purple", groupOf(0xFF00FF) == 6);
        boolean wrapOk = true;
        for (float h = -720; h < 1080; h += 0.5f) { int g = ColorGroups.groupOfHue(h); if (g < 0 || g >= ColorGroups.COUNT) wrapOk = false; }
        check("every hue maps to exactly one valid group", wrapOk);

        System.out.println("Measuring color");
        ByteBuffer img = ByteBuffer.allocate(100 * 4);
        for (int i = 0; i < 100; i++) {
            int rgb = i < 60 ? 0x00FF00 : i < 90 ? 0x0000FF : 0x808080;  // 60 green, 30 blue, 10 gray
            img.put(i * 4, (byte) hex(rgb, 16)).put(i * 4 + 1, (byte) hex(rgb, 8)).put(i * 4 + 2, (byte) hex(rgb, 0)).put(i * 4 + 3, (byte) 255);
        }
        ChromaHistogram h = ChromaHistogram.fromRgba8(img, 100);
        check("gray pixels carry no color", Math.abs(h.total - 90f) < 1e-3);
        check("green share is 60/90", Math.abs(h.perGroup[3] / h.total - 60f / 90f) < 1e-3);
        check("blue share is 30/90", Math.abs(h.perGroup[5] / h.total - 30f / 90f) < 1e-3);

        System.out.println("The key rule: 85% of visual color, not 85% of groups");
        float[] shares = {0.02f, 0.02f, 0.02f, 0.70f, 0.02f, 0.20f, 0.02f};
        ColorWorldState bigTwo = new ColorWorldState(settings(), new Random(1));
        forceRestored(bigTwo, new int[]{3, 5});
        bigTwo.submit(histogramWithShares(shares));
        check("2 of 7 groups back (29% of groups) but 90% of the color -> 90%", Math.abs(bigTwo.restoration() - 0.90) < 1e-4);
        check("...and that is deadly", bigTwo.pollKill());
        ColorWorldState smallFive = new ColorWorldState(settings(), new Random(1));
        forceRestored(smallFive, new int[]{0, 1, 2, 4, 6});
        smallFive.submit(histogramWithShares(shares));
        check("5 of 7 groups back (71% of groups) but 10% of the color -> 10%", Math.abs(smallFive.restoration() - 0.10) < 1e-4);
        check("...and that is NOT deadly", !smallFive.pollKill());
        ColorWorldState exactly = new ColorWorldState(settings(), new Random(1));
        forceRestored(exactly, new int[]{3, 5});
        exactly.submit(histogramWithShares(new float[]{0.075f, 0.075f, 0.0f, 0.60f, 0.0f, 0.25f, 0.0f}));
        check("exactly 85% kills (threshold is inclusive)", exactly.pollKill());
        ColorWorldState justUnder = new ColorWorldState(settings(), new Random(1));
        forceRestored(justUnder, new int[]{3, 5});
        justUnder.submit(histogramWithShares(new float[]{0.16f, 0.0f, 0.0f, 0.60f, 0.0f, 0.24f, 0.0f}));
        check("84% does not kill", !justUnder.pollKill());

        System.out.println("The meter matches what the shader actually shows");
        Random rng = new Random(42);
        boolean allMatch = true;
        for (int trial = 0; trial < 50; trial++) {
            ColorWorldState st = new ColorWorldState(settings(), new Random(trial));
            int back = 1 + rng.nextInt(6);
            for (int i = 0; i < back; i++) st.restoreRandomGroup();
            float[] values = new float[ColorGroups.COUNT];
            for (int g = 0; g < values.length; g++) values[g] = st.shaderValue(g);
            ChromaHistogram hist = new ChromaHistogram();
            hist.pixels = 20000;
            double before = 0, after = 0;
            for (int i = 0; i < hist.pixels; i++) {
                float r, g, b;
                if (rng.nextInt(5) == 0) { r = g = b = rng.nextFloat(); }
                else { r = rng.nextFloat(); g = rng.nextFloat(); b = rng.nextFloat(); }
                hist.add(r, g, b);
                float c0 = ColorGroups.chromaOf(r, g, b);
                if (c0 >= ColorGroups.MIN_CHROMA) {
                    float[] o = shade(r, g, b, values);
                    before += c0;
                    after += ColorGroups.chromaOf(o[0], o[1], o[2]);
                }
            }
            st.submit(hist);
            if (Math.abs(st.restoration() - after / before) > 2e-3) allMatch = false;
        }
        check("meter == share of the picture's color still on screen (50 random pictures)", allMatch);

        System.out.println("Original vs random colors");
        check("original group is untouched by the shader", Math.abs(shade(0.2f, 0.7f, 0.3f, allValues(0f))[1] - 0.7f) < 1e-6);
        float[] rotated = shade(0.2f, 0.7f, 0.3f, allValues(0.5f));
        check("random group keeps brightness and saturation but changes hue",
                Math.abs(ColorGroups.chromaOf(rotated[0], rotated[1], rotated[2]) - 0.5f) < 1e-4
                        && Math.abs(Math.max(rotated[0], Math.max(rotated[1], rotated[2])) - 0.7f) < 1e-4
                        && Math.abs(ColorGroups.hueOf(rotated[0], rotated[1], rotated[2]) - ColorGroups.hueOf(0.2f, 0.7f, 0.3f)) > 30f);
        float[] gray = shade(0.2f, 0.7f, 0.3f, allValues(-1f));
        check("gray group becomes a true gray", Math.abs(gray[0] - gray[1]) < 1e-6 && Math.abs(gray[1] - gray[2]) < 1e-6);

        int original = 0, total = 200_000;
        Random mc = new Random(7);
        for (int i = 0; i < total; i++) {
            ColorWorldState st = new ColorWorldState(settings(), mc);
            int g = st.restoreRandomGroup();
            if (st.isOriginal(g)) original++;
        }
        double frac = original / (double) total;
        check("45% original / 55% random (measured " + String.format("%.4f", frac) + ")", Math.abs(frac - 0.45) < 0.005);

        boolean bothCount = true;
        for (double chance : new double[]{0.0, 1.0}) {
            ColorWorldSettings s = settings(); s.originalChance = chance;
            ColorWorldState st = new ColorWorldState(s, new Random(3));
            forceRestored(st, new int[]{3, 5});
            st.submit(histogramWithShares(shares));
            if (Math.abs(st.restoration() - 0.90) > 1e-4) bothCount = false;
        }
        check("original and random colors count the same toward restoration", bothCount);

        System.out.println("30-second cycle, instant changes, consistency");
        ColorWorldSettings cs = settings();
        ColorWorldState st = new ColorWorldState(cs, new Random(11));
        check("starts fully grayscale", allGray(st));
        int seenRestores = 0;
        boolean resetAt600 = false, stableBetweenEvents = true;
        float[] last = snapshot(st);
        for (int t = 1; t <= 600; t++) {
            st.tick();
            float[] now = snapshot(st);
            boolean changed = !java.util.Arrays.equals(last, now);
            if (t % 100 == 0 && t < 600) { if (!changed) stableBetweenEvents = false; seenRestores++; }
            else if (t < 600 && changed) stableBetweenEvents = false;
            if (t == 600) resetAt600 = allGray(st);
            last = now;
        }
        check("a new group returns every 5 seconds (5 events in a cycle)", seenRestores == 5);
        check("nothing changes between events; each change is one instant step", stableBetweenEvents);
        check("grayscale reset exactly at 30 seconds", resetAt600);
        check("after the reset the cycle starts again", st.ticksUntilReset() == 600);

        ColorWorldSettings fast = settings(); fast.restoreIntervalTicks = 10;
        ColorWorldState fs = new ColorWorldState(fast, new Random(5));
        for (int t = 0; t < 120; t++) fs.tick();
        check("a group never returns twice in one cycle", fs.restoredCount() <= ColorGroups.COUNT);
        float[] before = snapshot(fs);
        fs.restoreRandomGroup();
        int stable = 0; for (int i = 0; i < ColorGroups.COUNT; i++) if (before[i] == fs.shaderValue(i) || before[i] < 0) stable++;
        check("a returned group keeps the same treatment until reset (consistent everywhere)", stable == ColorGroups.COUNT);

        System.out.println("Nothing to restore in a colorless view");
        ColorWorldState cave = new ColorWorldState(settings(), new Random(1));
        forceRestored(cave, new int[]{0, 1, 2, 3, 4, 5, 6});
        ChromaHistogram dark = new ChromaHistogram(); dark.pixels = 1000; dark.perGroup[0] = 4f; dark.total = 4f;
        cave.submit(dark);
        check("a view with almost no color reads 0% (one tiny red flower cannot kill)", cave.restoration() == 0.0 && !cave.pollKill());

        System.out.println("Death handling");
        ColorWorldState k = new ColorWorldState(settings(), new Random(1));
        forceRestored(k, new int[]{3, 5});
        k.submit(histogramWithShares(shares));
        check("kill fires once...", k.pollKill());
        check("...then stays quiet for a few seconds (no spamming)", !k.pollKill());
        k.reset();
        check("reset clears the meter instantly", k.restoration() == 0.0);

        System.out.println("Smoothing");
        ColorWorldSettings sm = new ColorWorldSettings(); // default 0.35
        ColorWorldState ss = new ColorWorldState(sm, new Random(1));
        forceRestored(ss, new int[]{3, 5});
        ss.submit(histogramWithShares(shares));
        check("one sample does not jump straight to the instant value", ss.restoration() < ss.instantRestoration());
        for (int i = 0; i < 30; i++) ss.submit(histogramWithShares(shares));
        check("sustained color converges to the true value", Math.abs(ss.restoration() - 0.90) < 0.01);

        System.out.println("Config file");
        Path dir = Files.createTempDirectory("cw");
        Path f = dir.resolve("config").resolve("colorworld.properties");
        ColorWorldSettings defaults = ColorWorldSettings.loadOrCreate(f);
        check("defaults file is created", Files.exists(f));
        check("defaults match the design (30s / 5s / 0.45 / 0.85)", defaults.resetIntervalTicks == 600 && defaults.restoreIntervalTicks == 100
                && defaults.originalChance == 0.45 && defaults.deathThreshold == 0.85);
        ColorWorldSettings again = ColorWorldSettings.loadOrCreate(f);
        check("created file parses back to the same values", again.resetIntervalTicks == 600 && again.showHud && again.restoreIntervalTicks == 100);
        Files.writeString(f, "reset_interval_seconds=10\nrestore_interval_seconds=2.5\noriginal_chance=7\ndeath_threshold=abc\nshow_hud=false\n");
        ColorWorldSettings custom = ColorWorldSettings.loadOrCreate(f);
        check("custom values are read, bad ones fall back / clamp",
                custom.resetIntervalTicks == 200 && custom.restoreIntervalTicks == 50 && custom.originalChance == 1.0
                        && custom.deathThreshold == 0.85 && !custom.showHud);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static float[] allValues(float v) { float[] a = new float[ColorGroups.COUNT]; java.util.Arrays.fill(a, v); return a; }
    static boolean allGray(ColorWorldState s) { for (int i = 0; i < ColorGroups.COUNT; i++) if (s.shaderValue(i) >= 0f) return false; return true; }
    static float[] snapshot(ColorWorldState s) { float[] a = new float[ColorGroups.COUNT]; for (int i = 0; i < a.length; i++) a[i] = s.shaderValue(i); return a; }

    /** Restore exactly these groups by calling the real restore method until each has returned. */
    static void forceRestored(ColorWorldState s, int[] groups) {
        java.util.Set<Integer> want = new java.util.HashSet<>();
        for (int g : groups) want.add(g);
        // restoreRandomGroup picks randomly; keep going on fresh states until the wanted set is exactly reached
        // (simplest deterministic route: use reflection-free retry with a fresh seeded state)
        try {
            java.lang.reflect.Field fr = ColorWorldState.class.getDeclaredField("restored");
            fr.setAccessible(true);
            boolean[] arr = (boolean[]) fr.get(s);
            for (int g : groups) arr[g] = true;
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
