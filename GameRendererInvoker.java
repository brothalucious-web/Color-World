package com.colorworld.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the mod switch the vanilla full-screen post-processing effect slot to our effect. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("setPostProcessor")
    void colorworld$setPostProcessor(Identifier id);

    @Invoker("clearPostProcessor")
    void colorworld$clearPostProcessor();

    @Invoker("getPostProcessorId")
    Identifier colorworld$getPostProcessorId();
}
