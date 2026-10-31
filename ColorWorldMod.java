package com.colorworld;

import com.colorworld.network.KillRequestPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColorWorldMod implements ModInitializer {
    public static final String MOD_ID = "colorworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(KillRequestPayload.ID, KillRequestPayload.CODEC);

        // The client measured that the world's color is 85% back: carry out the death.
        ServerPlayNetworking.registerGlobalReceiver(KillRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (player.isAlive() && !player.isSpectator()) {
                    player.kill(player.getServerWorld());
                }
            });
        });
        LOGGER.info("Color World loaded");
    }
}
