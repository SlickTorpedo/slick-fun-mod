package com.slickfun.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.slickfun.registry.ModGameRules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.world.World;

/**
 * Lets a server switch off creeper terrain damage without touching {@code mobGriefing}.
 *
 * <p>{@code mobGriefing} is a blunt instrument - it also stops snow golems laying snow,
 * villagers farming, and endermen moving blocks, which breaks a lot of farms. This changes
 * only the explosion's source type, from {@code MOB} (which destroys blocks) to {@code NONE}
 * (which does not). Entity damage is unaffected, so creepers still hurt just as much.
 */
@Mixin(CreeperEntity.class)
public abstract class CreeperExplosionMixin extends Entity {
	private CreeperExplosionMixin() {
		super(null, null);
	}

	@ModifyArg(
			method = "explode",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;createExplosion(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/world/World$ExplosionSourceType;)Lnet/minecraft/world/explosion/Explosion;"
			),
			index = 5
	)
	private World.ExplosionSourceType slickfun$softenExplosion(World.ExplosionSourceType original) {
		if (this.getWorld().getGameRules().getBoolean(ModGameRules.CREEPER_BLOCK_DAMAGE)) {
			return original;
		}

		return World.ExplosionSourceType.NONE;
	}
}
