package com.colorworld.mixin.client;

import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostEffectPass.class)
public interface PostEffectPassAccessor {
    @Accessor("program")
    ShaderProgram colorworld$getProgram();
}
