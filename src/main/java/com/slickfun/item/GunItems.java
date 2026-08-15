package com.slickfun.item;

import java.util.List;

import com.slickfun.util.Ballistics;
import com.slickfun.util.RpgManager;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** The armoury. One of these is a practical joke; the other two mean it. */
public final class GunItems {
	private GunItems() {
	}

	/**
	 * A working pistol. It fires downwards, into your own foot, every single time.
	 *
	 * <p>There is no aiming code because there is nothing to aim - the target is always the
	 * person holding it.
	 */
	public static class Pistol extends Item {
		private static final int COOLDOWN_TICKS = 40;
		private static final float DAMAGE = 3.0F;
		private static final int LIMP_TICKS = 20 * 15;

		public Pistol(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld level)) {
				return TypedActionResult.fail(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 1.9F);
			level.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.1D, player.getZ(),
					25, 0.2D, 0.05D, 0.2D, 0.02D);
			level.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1D, player.getZ(),
					15, 0.2D, 0.05D, 0.2D, 0.1D);

			player.damage(level.getDamageSources().generic(), DAMAGE);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, LIMP_TICKS, 2, false, true, true));
			player.sendMessage(Text.translatable("message.slickfun.pistol").formatted(Formatting.RED), false);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.pistol").formatted(Formatting.GRAY));
		}
	}

	/**
	 * Hitscan, unlimited ammo, and it will only ever hurt a player.
	 *
	 * <p>The ray is walked in short steps rather than handed to the entity raycast helper so
	 * that the block check and the player check happen at the same point along it - that is
	 * what stops it shooting cleanly through a wall.
	 */
	public static class AssaultRifle extends MoreGuns.Automatic {
		private static final float DAMAGE = 5.0F;
		private static final double RANGE = 60.0D;

		public AssaultRifle(Settings settings) {
			super(settings);
		}

		@Override
		protected int interval() {
			return 3;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter, int heldTicks) {
			Vec3d aim = Ballistics.spread(level, shooter.getRotationVec(1.0F), 0.04D);

			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
					SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5F, 2.0F);

			for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(), aim,
					RANGE, 0.35D, 0.65D, 1, ParticleTypes.CRIT)) {
				Ballistics.hurt(level, shooter, hit, DAMAGE);
				level.playSound(null, hit.getX(), hit.getY(), hit.getZ(),
						SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 1.0F, 1.2F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.assault_rifle").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.assault_rifle.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/**
	 * Fires a real, visible round. Where it lands, the world appears to come apart and does
	 * not actually move an inch.
	 *
	 * <p>The round is a snowball wearing a fire charge, because a custom projectile entity
	 * would need a client-side renderer to be visible at all and this mod ships server code
	 * only. Vanilla already draws a thrown item, so it borrows that.
	 */
	public static class Rpg extends Item {
		private static final int COOLDOWN_TICKS = 60;
		private static final float SPEED = 1.6F;

		public Rpg(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity shooter) || !(world instanceof ServerWorld level)) {
				return TypedActionResult.fail(stack);
			}

			if (shooter.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			shooter.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			SnowballEntity round = new SnowballEntity(level, shooter);
			round.setItem(new ItemStack(Items.FIRE_CHARGE));
			round.setVelocity(shooter, shooter.getPitch(), shooter.getYaw(), 0.0F, SPEED, 0.4F);
			level.spawnEntity(round);

			RpgManager.track(round, shooter);

			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
					SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 2.0F, 0.5F);
			level.spawnParticles(ParticleTypes.LARGE_SMOKE,
					shooter.getX(), shooter.getEyeY() - 0.2D, shooter.getZ(), 30, 0.3D, 0.3D, 0.3D, 0.05D);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			// Same reasoning as the self destruct button: saying it is fake spoils it.
			tooltip.add(Text.translatable("tooltip.slickfun.rpg").formatted(Formatting.GRAY));
		}
	}
}
