package com.slickfun.client;

import com.slickfun.SlickFunMod;
import com.slickfun.registry.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Almost everything in this mod is driven from the server - particles, sounds and screens
 * are all vanilla-synced - so this stays small. The thrown Poke Ball is the exception: a
 * custom entity has to be told how to draw itself, and it draws as its own item.
 */
public class SlickFunModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.POKE_BALL, FlyingItemEntityRenderer::new);
		SlickFunMod.LOGGER.info("Slick Fun Mod client ready.");
	}
}
