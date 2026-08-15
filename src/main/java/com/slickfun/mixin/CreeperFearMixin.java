package com.slickfun.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slickfun.util.CatCharm;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Creepers already flee from cats and ocelots. This adds a third thing they are frightened
 * of: anyone wearing the Cat Collar. Same goal class, same priority, same distance vanilla
 * uses for cats, so it behaves identically to being followed around by one.
 */
@Mixin(CreeperEntity.class)
public abstract class CreeperFearMixin extends HostileEntity {
	private CreeperFearMixin(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void slickfun$fearTheCollar(CallbackInfo ci) {
		this.goalSelector.add(3, new FleeEntityGoal<>(
				this, PlayerEntity.class, CatCharm::isWornBy, 6.0F, 1.0, 1.2, CatCharm::isWornBy));
	}
}
