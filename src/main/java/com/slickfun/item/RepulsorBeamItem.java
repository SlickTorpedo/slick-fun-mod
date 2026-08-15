package com.slickfun.item;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.TargetHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** The tractor beam in reverse - one click, and whatever you are looking at is elsewhere. */
public class RepulsorBeamItem extends AdminItem {
	private static final double RANGE = 64.0D;
	private static final double PUSH_STRENGTH = 2.2D;
	private static final int COOLDOWN_TICKS = 20;

	public RepulsorBeamItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "repulsor_beam";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!AdminUtil.checkAdmin(user)) {
			return TypedActionResult.fail(stack);
		}

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		Entity target = TargetHelper.findTarget(user, RANGE);

		if (target == null) {
			user.sendMessage(Text.translatable("message.slickfun.no_target").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		ServerWorld serverWorld = (ServerWorld) world;
		drawBeam(serverWorld, TargetHelper.beamOrigin(user), target, ParticleTypes.FLAME);

		Vec3d push = target.getBoundingBox().getCenter()
				.subtract(user.getEyePos())
				.normalize()
				.multiply(PUSH_STRENGTH);

		target.setVelocity(push.x, Math.max(push.y, 0.45D), push.z);
		target.velocityModified = true;
		target.fallDistance = 0.0F;

		serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0F, 0.7F);
		serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
				target.getX(), target.getBodyY(0.5D), target.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.0D);

		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		user.swingHand(hand, true);
		return TypedActionResult.success(stack, false);
	}
}
