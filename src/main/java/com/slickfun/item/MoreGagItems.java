package com.slickfun.item;

import java.util.List;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Two more that promise well and deliver otherwise. */
public final class MoreGagItems {
	private MoreGagItems() {
	}

	/** A coin weighted entirely in one direction, and not yours. */
	public static class LuckyCoin extends Item {
		private static final int COOLDOWN_TICKS = 60;
		private static final int UNLUCK_TICKS = 20 * 30;

		public LuckyCoin(Settings settings) {
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

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.8F);
			player.getServerWorld().spawnParticles(ParticleTypes.WAX_OFF,
					player.getX(), player.getY() + 1.6D, player.getZ(), 12, 0.2D, 0.3D, 0.2D, 0.05D);

			// It is not a coin flip. There is only one side.
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.UNLUCK, UNLUCK_TICKS, 1, false, true, true));
			player.sendMessage(Text.translatable("message.slickfun.coin").formatted(Formatting.GRAY), false);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.lucky_coin").formatted(Formatting.GRAY));
		}
	}

	/** Nutritionally complete. Eating it makes you hungrier than you were. */
	public static class EmergencyRations extends Item {
		public EmergencyRations(Settings settings) {
			super(settings);
		}

		@Override
		public ItemStack finishUsing(ItemStack stack, World world, net.minecraft.entity.LivingEntity user) {
			ItemStack left = super.finishUsing(stack, world, user);

			if (!world.isClient && user instanceof ServerPlayerEntity player) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 45, 1, false, true, true));
				player.sendMessage(Text.translatable("message.slickfun.rations").formatted(Formatting.GRAY), true);
			}

			return left;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.rations").formatted(Formatting.GRAY));
		}
	}
}
