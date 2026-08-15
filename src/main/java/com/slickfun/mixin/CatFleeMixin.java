package com.slickfun.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slickfun.util.CatCharm;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.CatEntity;

/**
 * Stops untamed cats bolting from anyone wearing the Cat Collar.
 *
 * <p>Vanilla cats flee players from 16 blocks unless the player is sneaking. This cancels
 * that goal when its chosen target is a collar wearer, which is checked after the goal has
 * picked someone - so with two people nearby, the cat still flees the one without a collar.
 */
@Mixin(FleeEntityGoal.class)
public abstract class CatFleeMixin {
	@Shadow
	@Final
	protected PathAwareEntity mob;

	@Shadow
	protected LivingEntity targetEntity;

	@Inject(method = "canStart", at = @At("RETURN"), cancellable = true)
	private void slickfun$catsTrustCollarWearers(CallbackInfoReturnable<Boolean> cir) {
		if (shouldIgnore(cir.getReturnValue())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "shouldContinue", at = @At("RETURN"), cancellable = true)
	private void slickfun$catsKeepTrusting(CallbackInfoReturnable<Boolean> cir) {
		if (shouldIgnore(cir.getReturnValue())) {
			cir.setReturnValue(false);
		}
	}

	private boolean shouldIgnore(boolean fleeing) {
		return fleeing && this.mob instanceof CatEntity && CatCharm.isWornBy(this.targetEntity);
	}
}
