package com.colorworld.network;

import com.colorworld.ColorWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sent from the client to the server when the client has measured that 85% of the world's visual color has
 * returned. The color is a client-side thing (it is measured from the rendered picture), but a player can only
 * be killed by the server, so the client asks the server to do it.
 */
public record KillRequestPayload() implements CustomPayload {
    public static final CustomPayload.Id<KillRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ColorWorldMod.MOD_ID, "kill_request"));
    public static final PacketCodec<RegistryByteBuf, KillRequestPayload> CODEC =
            PacketCodec.unit(new KillRequestPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
