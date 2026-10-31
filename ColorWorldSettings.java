package com.colorworld.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tunable numbers. The design rules (45/55, 85%, 30s) are the defaults; everything can be changed in the config file. */
public final class ColorWorldSettings {
    /** Grayscale reset: the whole world goes gray again every this many ticks (600 = 30 seconds). */
    public int resetIntervalTicks = 600;
    /** One more color group returns every this many ticks (100 = 5 seconds). */
    public int restoreIntervalTicks = 100;
    /** Chance that a returning group shows its ORIGINAL colors. The rest of the time it gets RANDOM colors. */
    public double originalChance = 0.45;
    /** The player dies when this share of the world's visual color has returned. */
    public double deathThreshold = 0.85;
    /** If the picture has less color than this (average chroma per pixel) there is nothing to restore, so 0%. */
    public double minCoverage = 0.03;
    /** 0..1. How fast the measured percentage follows the picture. 1 = instant, smaller = steadier. */
    public double smoothing = 0.35;
    /** How often the picture is measured, in milliseconds. */
    public int sampleIntervalMs = 100;
    /** Show the small status text in the top-left corner. */
    public boolean showHud = true;

    private static final String DEFAULT_FILE = """
            # Color World settings. Delete this file to get the defaults back.

            # The world turns grayscale again every this many seconds.
            reset_interval_seconds=30

            # One more color group returns every this many seconds.
            restore_interval_seconds=5

            # Chance (0-1) that a returning group shows its original colors; otherwise it gets random colors.
            original_chance=0.45

            # You die when this share (0-1) of the world's visual color has returned.
            death_threshold=0.85

            # Below this much color on screen (0-1) the meter reads 0%.
            min_color_coverage=0.03

            # 0-1. How quickly the meter follows what is on screen (1 = instantly).
            smoothing=0.35

            # Show the status text in the top-left corner (true/false).
            show_hud=true
            """;

    /** Loads the file, writing the defaults first if it does not exist. Never throws: bad values fall back to defaults. */
    public static ColorWorldSettings loadOrCreate(Path file) {
        ColorWorldSettings s = new ColorWorldSettings();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
                return s;
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            s.applyProperties(p);
        } catch (IOException | RuntimeException e) {
            // keep defaults
        }
        return s;
    }

    public void applyProperties(Properties p) {
        resetIntervalTicks = ticks(p, "reset_interval_seconds", resetIntervalTicks);
        restoreIntervalTicks = ticks(p, "restore_interval_seconds", restoreIntervalTicks);
        originalChance = unit(p, "original_chance", originalChance);
        deathThreshold = unit(p, "death_threshold", deathThreshold);
        minCoverage = unit(p, "min_color_coverage", minCoverage);
        smoothing = Math.max(0.01, unit(p, "smoothing", smoothing));
        showHud = !"false".equalsIgnoreCase(p.getProperty("show_hud", "true").trim());
    }

    private static int ticks(Properties p, String key, int fallbackTicks) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallbackTicks;
        }
        try {
            double seconds = Double.parseDouble(v.trim());
            return Math.max(1, (int) Math.round(seconds * 20.0));
        } catch (NumberFormatException e) {
            return fallbackTicks;
        }
    }

    private static double unit(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(v.trim());
            return Math.min(1.0, Math.max(0.0, d));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
