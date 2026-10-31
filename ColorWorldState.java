package com.colorworld.core;

import java.util.Random;

/**
 * The whole game rule set, with no Minecraft code in it so it can be tested on its own.
 *
 * <h3>The loop</h3>
 * <ol>
 *   <li>The world starts every cycle fully grayscale (0% restored).</li>
 *   <li>Every {@code restoreIntervalTicks} one more color group (red, orange, ... purple) returns, instantly.
 *       It returns with its ORIGINAL colors (45%) or with RANDOM colors (55%). Both count as "restored".</li>
 *   <li>{@link #submit(ChromaHistogram)} is fed a measurement of the real picture. The restoration meter is
 *       the share of the picture's total color that belongs to groups that have returned, so it is weighted
 *       by how much color you can actually see, not by how many groups came back.</li>
 *   <li>At {@code deathThreshold} (85%) {@link #pollKill()} fires.</li>
 *   <li>Every {@code resetIntervalTicks} (30 s) everything goes grayscale again.</li>
 * </ol>
 *
 * A group's treatment is only decided when it returns and never changes until the next reset, so every pixel
 * of that group looks the same everywhere in the world.
 */
public final class ColorWorldState {
    private static final int KILL_COOLDOWN_TICKS = 100;
    private static final float RANDOM_SHIFT_MIN = 0.10f;
    private static final float RANDOM_SHIFT_SPAN = 0.80f;

    private final ColorWorldSettings cfg;
    private final Random rng;

    private final boolean[] restored = new boolean[ColorGroups.COUNT];
    private final boolean[] original = new boolean[ColorGroups.COUNT];
    private final float[] shift = new float[ColorGroups.COUNT];
    private final float[] share = new float[ColorGroups.COUNT];

    private int tickInCycle;
    private float coverage;
    private double instantRestoration;
    private double restoration;
    private int killCooldown;

    public ColorWorldState(ColorWorldSettings cfg, Random rng) {
        this.cfg = cfg;
        this.rng = rng;
    }

    /** Everything back to grayscale and a fresh 30-second cycle. */
    public void reset() {
        for (int i = 0; i < ColorGroups.COUNT; i++) {
            restored[i] = false;
            original[i] = false;
            shift[i] = 0f;
        }
        tickInCycle = 0;
        instantRestoration = 0;
        restoration = 0;
    }

    /** Call once per game tick (not while paused). */
    public void tick() {
        if (killCooldown > 0) {
            killCooldown--;
        }
        tickInCycle++;
        if (tickInCycle >= cfg.resetIntervalTicks) {
            reset();
            return;
        }
        if (tickInCycle % cfg.restoreIntervalTicks == 0) {
            restoreRandomGroup();
        }
    }

    /** Brings back one random group that is still gray. Returns its index, or -1 if all are already back. */
    public int restoreRandomGroup() {
        int remaining = 0;
        for (boolean r : restored) {
            if (!r) {
                remaining++;
            }
        }
        if (remaining == 0) {
            return -1;
        }
        int pick = rng.nextInt(remaining);
        for (int i = 0; i < ColorGroups.COUNT; i++) {
            if (restored[i]) {
                continue;
            }
            if (pick-- == 0) {
                restored[i] = true;
                original[i] = rng.nextDouble() < cfg.originalChance;
                shift[i] = original[i] ? 0f : RANDOM_SHIFT_MIN + rng.nextFloat() * RANDOM_SHIFT_SPAN;
                recomputeInstant();
                return i;
            }
        }
        return -1;
    }

    /** Feeds in a measurement of the current picture (original colors, before the effect is applied). */
    public void submit(ChromaHistogram h) {
        coverage = h.coverage();
        for (int i = 0; i < ColorGroups.COUNT; i++) {
            share[i] = h.total > 0f ? h.perGroup[i] / h.total : 0f;
        }
        recomputeInstant();
        restoration += cfg.smoothing * (instantRestoration - restoration);
    }

    private void recomputeInstant() {
        if (coverage < cfg.minCoverage) {
            instantRestoration = 0;
            return;
        }
        double sum = 0;
        for (int i = 0; i < ColorGroups.COUNT; i++) {
            if (restored[i]) {
                sum += share[i];
            }
        }
        instantRestoration = Math.min(1.0, sum);
    }

    /** True once when the meter is at or above the death threshold; then stays quiet for a few seconds. */
    public boolean pollKill() {
        if (killCooldown == 0 && restoration >= cfg.deathThreshold) {
            killCooldown = KILL_COOLDOWN_TICKS;
            return true;
        }
        return false;
    }

    /** Value handed to the shader for a group: -1 = still gray, otherwise hue rotation in turns (0 = original colors). */
    public float shaderValue(int group) {
        return restored[group] ? shift[group] : -1f;
    }

    /** 0..1 share of the world's visual color that has returned (smoothed). */
    public double restoration() {
        return restoration;
    }

    /** Same, but without smoothing. */
    public double instantRestoration() {
        return instantRestoration;
    }

    public boolean isRestored(int group) {
        return restored[group];
    }

    public boolean isOriginal(int group) {
        return original[group];
    }

    public int restoredCount() {
        int n = 0;
        for (boolean r : restored) {
            if (r) {
                n++;
            }
        }
        return n;
    }

    public int ticksUntilReset() {
        return Math.max(0, cfg.resetIntervalTicks - tickInCycle);
    }
}
