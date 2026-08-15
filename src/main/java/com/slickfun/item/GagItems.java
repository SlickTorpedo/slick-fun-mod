package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModArmorMaterials;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Equipment that promises a great deal and delivers none of it.
 *
 * <p>Every one of these is written to be convincing right up to the moment it matters: the
 * sounds are real, the particles are real, the tooltip is confident. Only the effect is
 * missing, or backwards.
 */
public final class GagItems {
	private GagItems() {
	}

	/**
	 * Full body protection. Provides none, and will not let you stand still either.
	 *
	 * <p>Registered as genuine armour with a real material so it equips into the chest slot
	 * and renders on you - a fake that only sat in your inventory would give itself away.
	 */
	public static class BubbleSuit extends ArmorItem {
		public BubbleSuit(Settings settings) {
			super(ModArmorMaterials.BUBBLE, ArmorItem.Type.CHESTPLATE, settings);
		}

		/** Called each cycle by the gear manager for anyone wearing one. */
		public static void wobble(ServerPlayerEntity player) {
			ServerWorld world = player.getServerWorld();
			Vec3d drift = player.getVelocity();

			// Up, and gently sideways, so standing still is not an option.
			double wander = (world.getRandom().nextDouble() - 0.5D) * 0.12D;
			double sway = (world.getRandom().nextDouble() - 0.5D) * 0.12D;

			player.setVelocity(drift.x + wander, Math.min(0.42D, drift.y + 0.22D), drift.z + sway);
			player.velocityModified = true;
			player.fallDistance = 0.0F;

			world.spawnParticles(ParticleTypes.BUBBLE_POP,
					player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.4D, 0.6D, 0.4D, 0.02D);

			if (world.getRandom().nextInt(3) == 0) {
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_BOAT_PADDLE_WATER, SoundCategory.PLAYERS,
						0.8F, 1.2F + world.getRandom().nextFloat() * 0.6F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.bubble_suit").formatted(Formatting.AQUA));
			tooltip.add(Text.translatable("tooltip.slickfun.bubble_suit.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Deploys perfectly every time. Deployment has no effect on anything. */
	public static class EmergencyParachute extends Item {
		public EmergencyParachute(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player)) {
				return TypedActionResult.fail(stack);
			}

			ServerWorld level = player.getServerWorld();

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 1.0F, 1.4F);
			level.spawnParticles(ParticleTypes.CLOUD,
					player.getX(), player.getY() + 2.0D, player.getZ(), 30, 0.6D, 0.2D, 0.6D, 0.02D);

			// Deliberately nothing else. No velocity change, no fall distance reset.
			player.sendMessage(Text.translatable("message.slickfun.parachute").formatted(Formatting.GREEN), true);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.parachute").formatted(Formatting.GRAY));
		}
	}

	/** Total silence, guaranteed. Carried, they make everything considerably worse. */
	public static class NoiseCancellingHeadphones extends Item {
		public NoiseCancellingHeadphones(Settings settings) {
			super(settings);
		}

		/** Called each cycle by the gear manager for anyone carrying a pair. */
		public static void amplify(ServerPlayerEntity player) {
			ServerWorld world = player.getServerWorld();

			if (world.getRandom().nextInt(5) != 0) {
				return;
			}

			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS,
					1.6F, 0.5F + world.getRandom().nextFloat() * 1.4F);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.headphones").formatted(Formatting.GRAY));
		}
	}

	/** Instant escape from any situation. The situation is one block away. */
	public static class EmergencyExit extends Item {
		private static final int COOLDOWN_TICKS = 10;

		public EmergencyExit(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player)) {
				return TypedActionResult.fail(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			ServerWorld level = player.getServerWorld();
			Vec3d back = escapeRoute(level, player);

			if (back == null) {
				player.sendMessage(Text.translatable("message.slickfun.exit.blocked").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.PLAYERS, 1.0F, 1.0F);
			level.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
					30, 0.4D, 0.6D, 0.4D, 0.3D);

			// The full extent of the escape.
			player.requestTeleport(back.x, back.y, back.z);
			player.fallDistance = 0.0F;

			level.playSound(null, back.x, back.y, back.z,
					SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.exit").formatted(Formatting.GREEN), true);

			return TypedActionResult.success(stack);
		}

		/**
		 * One block behind, at the same height, and only if there is actually room to stand.
		 *
		 * <p>The step is flattened to the horizontal before it is used, or looking at your feet
		 * would drive you into the floor. Even then the space has to be checked - backing into
		 * a wall would otherwise bury you inside it.
		 */
		private static Vec3d escapeRoute(ServerWorld level, ServerPlayerEntity player) {
			Vec3d facing = player.getRotationVec(1.0F).multiply(1.0D, 0.0D, 1.0D);

			if (facing.lengthSquared() < 1.0E-4D) {
				return null;
			}

			Vec3d back = player.getPos().subtract(facing.normalize());

			// Same level first, then a step up in case there is a lip behind them.
			for (double lift : new double[] {0.0D, 1.0D}) {
				Vec3d candidate = back.add(0.0D, lift, 0.0D);

				if (roomToStand(level, candidate)) {
					return candidate;
				}
			}

			return null;
		}

		private static boolean roomToStand(ServerWorld level, Vec3d feet) {
			BlockPos at = BlockPos.ofFloored(feet);

			if (!level.isInBuildLimit(at)) {
				return false;
			}

			return level.getBlockState(at).getCollisionShape(level, at).isEmpty()
					&& level.getBlockState(at.up()).getCollisionShape(level, at.up()).isEmpty();
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.emergency_exit").formatted(Formatting.GRAY));
		}
	}
}
