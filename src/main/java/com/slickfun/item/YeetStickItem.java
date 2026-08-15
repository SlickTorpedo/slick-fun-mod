package com.slickfun.item;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.TargetHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

/** Launches the target straight into orbit. Slow falling is included, out of kindness. */
public class YeetStickItem extends AdminItem {
	private static final double RANGE = 48.0D;
	private static final int COOLDOWN_TICKS = 20;

	public YeetStickItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "yeet_stick";
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
		Vec3d look = user.getRotationVec(1.0F);

		target.setVelocity(look.x * 0.7D, 2.4D, look.z * 0.7D);
		target.velocityModified = true;
		target.fallDistance = 0.0F;

		if (target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false, true));
		}

		serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0F, 1.0F);
		serverWorld.spawnParticles(ParticleTypes.CLOUD,
				target.getX(), target.getY(), target.getZ(), 30, 0.3D, 0.1D, 0.3D, 0.1D);

		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		user.swingHand(hand, true);
		return TypedActionResult.success(stack, false);
	}
}
