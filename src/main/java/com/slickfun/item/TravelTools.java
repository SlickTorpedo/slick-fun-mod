package com.slickfun.item;

import java.util.List;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Getting about. */
public final class TravelTools {
	private TravelTools() {
	}

	/**
	 * A boat that tidies itself away.
	 *
	 * <p>The boat is tracked and removed the moment you step out, so shorelines stop filling
	 * up with abandoned ones.
	 */
	public static class PocketBoat extends PortableUtilityItem {
		public PocketBoat(Settings settings) {
			super(settings);
		}

		@Override
		protected String tooltipKey() {
			return "pocket_boat";
		}

		/** Used from the knife, where there is no block to aim at: drop it at your feet. */
		@Override
		public void openFor(ServerPlayerEntity player, com.slickfun.util.ToolHost host) {
			launch(player, player.getBlockPos());
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return ActionResult.FAIL;
			}

			return launch(player, context.getBlockPos().offset(context.getSide())) ? ActionResult.SUCCESS : ActionResult.FAIL;
		}

		private static boolean launch(ServerPlayerEntity player, BlockPos target) {
			if (player.hasVehicle()) {
				return false;
			}

			ServerWorld world = player.getServerWorld();
			BoatEntity boat = EntityType.BOAT.create(world);

			if (boat == null) {
				return false;
			}

			boat.refreshPositionAndAngles(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, player.getYaw(), 0.0F);

			if (!world.spawnEntity(boat)) {
				return false;
			}

			player.startRiding(boat, true);
			com.slickfun.util.BoatManager.track(boat);

			world.playSound(null, target, SoundEvents.ENTITY_BOAT_PADDLE_WATER, SoundCategory.PLAYERS, 0.8F, 1.0F);
			return true;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.pocket_boat.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.pocket_boat.2").formatted(Formatting.DARK_GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.fits_in_knife").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Fast, but only on ice. Rewards anyone who builds a proper ice road. */
	public static class SpeedSkates extends Item {
		public SpeedSkates(Settings settings) {
			super(settings);
		}

		/** True if this block is worth skating on. */
		public static boolean isIce(World world, BlockPos pos) {
			return world.getBlockState(pos).isOf(Blocks.ICE)
					|| world.getBlockState(pos).isOf(Blocks.PACKED_ICE)
					|| world.getBlockState(pos).isOf(Blocks.BLUE_ICE)
					|| world.getBlockState(pos).isOf(Blocks.FROSTED_ICE);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.speed_skates.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.speed_skates.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** A shove straight up, and something to break the landing. */
	public static class UpdraftCharm extends Item {
		public UpdraftCharm(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			user.setVelocity(user.getVelocity().x, 1.35D, user.getVelocity().z);
			user.velocityModified = true;
			user.fallDistance = 0.0F;
			user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, false, true));

			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 1.0F, 1.2F);

			if (world instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.CLOUD,
						user.getX(), user.getY(), user.getZ(), 30, 0.3D, 0.1D, 0.3D, 0.1D);
			}

			if (!user.getAbilities().creativeMode) {
				stack.decrement(1);
			}

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.updraft_charm.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.updraft_charm.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** No fall damage, and a bounce on the way back up. */
	public static class SlimeBoots extends Item {
		public SlimeBoots(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.slime_boots.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.slime_boots.2").formatted(Formatting.DARK_GRAY));
		}
	}
}
