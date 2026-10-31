package com.colorworld.client;

import com.colorworld.ColorWorldMod;
import com.colorworld.core.ChromaHistogram;
import com.colorworld.core.ColorGroups;
import com.colorworld.core.ColorWorldSettings;
import com.colorworld.core.ColorWorldState;
import com.colorworld.mixin.client.GameRendererInvoker;
import com.colorworld.mixin.client.PostEffectPassAccessor;
import com.colorworld.mixin.client.PostEffectProcessorAccessor;
import com.colorworld.network.KillRequestPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Random;

/**
 * Client side of Color World: runs the rule set, keeps the full-screen color effect switched on, measures the
 * picture, and asks the server for the death when the world's color is 85% back.
 */
public final class ColorWorldClient implements ClientModInitializer {
    /** assets/colorworld/post_effect/color_world.json */
    public static final Identifier EFFECT_ID = Identifier.of(ColorWorldMod.MOD_ID, "color_world");

    private static final String[] UNIFORMS = new String[ColorGroups.COUNT];
    private static final int MAX_EFFECT_RETRIES = 5;

    static {
        for (int i = 0; i < UNIFORMS.length; i++) {
            UNIFORMS[i] = "CwG" + i;
        }
    }

    private static ColorWorldSettings settings = new ColorWorldSettings();
    private static ColorWorldState state = new ColorWorldState(settings, new Random());
    private static final FrameSampler SAMPLER = new FrameSampler();

    private static ClientPlayerEntity lastPlayer;
    private static long lastSampleNanos;
    private static int effectRetries;

    @Override
    public void onInitializeClient() {
        settings = ColorWorldSettings.loadOrCreate(FabricLoader.getInstance().getConfigDir().resolve("colorworld.properties"));
        state = new ColorWorldState(settings, new Random());
        ClientTickEvents.END_CLIENT_TICK.register(ColorWorldClient::onTick);
        HudRenderCallback.EVENT.register(ColorWorldClient::onHud);
    }

    // ------------------------------------------------------------------ tick

    private static void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (client.world == null || player == null) {
            releaseEffect(client);
            lastPlayer = null;
            return;
        }
        ensureEffect(client);

        if (player != lastPlayer) {
            // joined a world, respawned, or changed dimension: start a fresh grayscale cycle
            lastPlayer = player;
            state.reset();
            return;
        }
        if (client.isPaused() || player.isDead()) {
            return;
        }

        state.tick();
        if (state.pollKill() && ClientPlayNetworking.canSend(KillRequestPayload.ID)) {
            ClientPlayNetworking.send(new KillRequestPayload());
        }
    }

    private static void ensureEffect(MinecraftClient client) {
        GameRendererInvoker renderer = (GameRendererInvoker) client.gameRenderer;
        if (EFFECT_ID.equals(renderer.colorworld$getPostProcessorId())) {
            effectRetries = 0;
            return;
        }
        if (effectRetries >= MAX_EFFECT_RETRIES) {
            return; // the effect would not load; the reason is in the game log
        }
        effectRetries++;
        renderer.colorworld$setPostProcessor(EFFECT_ID);
    }

    private static void releaseEffect(MinecraftClient client) {
        GameRendererInvoker renderer = (GameRendererInvoker) client.gameRenderer;
        if (EFFECT_ID.equals(renderer.colorworld$getPostProcessorId())) {
            renderer.colorworld$clearPostProcessor();
        }
        effectRetries = 0;
    }

    // ------------------------------------------------- called from the mixin

    /** True if this post-effect processor is ours (it has our uniforms). */
    public static boolean isOurEffect(List<PostEffectPass> passes) {
        for (PostEffectPass pass : passes) {
            ShaderProgram program = ((PostEffectPassAccessor) pass).colorworld$getProgram();
            if (program != null && program.getUniform(UNIFORMS[0]) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs right before our effect draws. At this moment the main framebuffer still holds the world in its
     * ORIGINAL colors, so this is where the picture is measured. Then the current group states are pushed
     * to the shader.
     */
    public static void beforeEffect(PostEffectProcessor processor) {
        long now = System.nanoTime();
        if ((now - lastSampleNanos) / 1_000_000L >= settings.sampleIntervalMs) {
            lastSampleNanos = now;
            ChromaHistogram histogram = SAMPLER.sample(MinecraftClient.getInstance().getFramebuffer());
            if (histogram != null) {
                state.submit(histogram);
            }
        }
        PostEffectProcessorAccessor accessor = (PostEffectProcessorAccessor) processor;
        for (int i = 0; i < ColorGroups.COUNT; i++) {
            accessor.colorworld$setUniforms(UNIFORMS[i], state.shaderValue(i));
        }
    }

    // ------------------------------------------------------------------- HUD

    private static void onHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!settings.showHud || client.player == null || client.options.hudHidden) {
            return;
        }
        int percent = (int) Math.round(state.restoration() * 100.0);
        int limit = (int) Math.round(settings.deathThreshold * 100.0);
        int seconds = (state.ticksUntilReset() + 19) / 20;

        StringBuilder back = new StringBuilder();
        for (int i = 0; i < ColorGroups.COUNT; i++) {
            if (!state.isRestored(i)) {
                continue;
            }
            if (back.length() > 0) {
                back.append(", ");
            }
            back.append(ColorGroups.NAMES[i]).append(state.isOriginal(i) ? " (original)" : " (random)");
        }

        int white = 0xFFFFFFFF;
        int warn = percent >= limit - 15 ? 0xFFFF5555 : white;
        context.drawTextWithShadow(client.textRenderer, "Color restored: " + percent + "%  (death at " + limit + "%)", 6, 6, warn);
        context.drawTextWithShadow(client.textRenderer, "Grayscale reset in " + seconds + "s", 6, 17, white);
        context.drawTextWithShadow(client.textRenderer, back.length() == 0 ? "Returned: nothing yet" : "Returned: " + back, 6, 28, white);
    }
}
