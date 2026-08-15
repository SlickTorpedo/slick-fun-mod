package com.slickfun.registry;

import com.slickfun.SlickFunMod;
import com.slickfun.entity.PokeBallEntity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<PokeBallEntity> POKE_BALL = Registry.register(
			Registries.ENTITY_TYPE, SlickFunMod.id("poke_ball"),
			EntityType.Builder.<PokeBallEntity>create(PokeBallEntity::new, SpawnGroup.MISC)
					.dimensions(0.25F, 0.25F)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("poke_ball"));

	private ModEntities() {
	}

	public static void register() {
		SlickFunMod.LOGGER.info("Registered entities.");
	}
}
