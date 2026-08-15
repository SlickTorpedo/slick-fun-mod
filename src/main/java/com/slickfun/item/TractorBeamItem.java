package com.slickfun.item;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.TargetHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Hold right click to lock onto whatever is under your crosshair and reel it in. Releases
 * as soon as you let go, so you can dangle people rather than only yanking them.
 */
public class TractorBeamItem extends AdminItem {
	public static final double RANGE = 64.0D;
	private static final double PULL_STRENGTH = 0.55D;

	public TractorBeamItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "tractor_beam";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!AdminUtil.checkAdmin(user)) {
			return TypedActionResult.fail(stack);
		}

		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world.isClient || !(user instanceof PlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
			return;
		}

		if (!AdminUtil.isAdmin(player)) {
			player.stopUsingItem();
			return;
		}

		Entity target = TargetHelper.findTarget(player, RANGE);

		if (target == null) {
			return;
		}

		Vec3d origin = TargetHelper.beamOrigin(player);
		drawBeam(serverWorld, origin, target, ParticleTypes.END_ROD);

		Vec3d pull = player.getEyePos()
				.subtract(target.getBoundingBox().getCenter())
				.normalize()
				.multiply(PULL_STRENGTH);

		// Damp existing motion so the target does not overshoot and orbit you.
		target.setVelocity(target.getVelocity().multiply(0.6D).add(pull));
		target.velocityModified = true;
		target.fallDistance = 0.0F;

		if (remainingUseTicks % 8 == 0) {
			serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.4F, 1.8F);
		}
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}
}
