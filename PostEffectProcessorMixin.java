package com.colorworld.mixin.client;

import com.colorworld.client.ColorWorldClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.FrameGraphBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the moment just before a post-processing effect draws. Vanilla's own effects (blur, transparency,
 * entity outlines) pass straight through; only an effect that carries our uniforms is handled.
 *
 * Both of the processor's render entry points end up in this frame-graph overload, so hooking it covers both.
 */
@Mixin(PostEffectProcessor.class)
public abstract class PostEffectProcessorMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/FrameGraphBuilder;IILnet/minecraft/client/gl/PostEffectProcessor$FramebufferSet;)V",
            at = @At("HEAD")
    )
    private void colorworld$beforeRender(FrameGraphBuilder builder, int textureWidth, int textureHeight,
                                         PostEffectProcessor.FramebufferSet framebufferSet, CallbackInfo ci) {
        PostEffectProcessor self = (PostEffectProcessor) (Object) this;
        if (ColorWorldClient.isOurEffect(((PostEffectProcessorAccessor) self).colorworld$getPasses())) {
            ColorWorldClient.beforeEffect(self);
        }
    }
}
