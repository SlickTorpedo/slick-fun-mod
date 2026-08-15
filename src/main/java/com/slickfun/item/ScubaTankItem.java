package com.slickfun.item;

import java.util.List;

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

/**
 * Bottled air. Carry it and it tops your breath up while you are under, spending itself as it
 * goes; surface and right click to refill.
 *
 * <p>Air is tracked as the item's own durability, so the bar on the icon is the gauge - no
 * extra component, and it is obvious at a glance how much is left.
 */
public class ScubaTankItem extends Item {
	/** Ticks of submerged time a full tank buys. Two and a half minutes. */
	public static final int CAPACITY = 20 * 150;

	private static final int REFILL_COOLDOWN_TICKS = 20 * 3;

	public ScubaTankItem(Settings settings) {
		super(settings);
	}

	public static int airLeft(ItemStack stack) {
		return stack.getMaxDamage() - stack.getDamage();
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

		if (stack.getDamage() == 0) {
			player.sendMessage(Text.translatable("message.slickfun.scuba.already_full").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		// You cannot fill a tank with the thing you are drowning in.
		if (player.isSubmergedInWater()) {
			player.sendMessage(Text.translatable("message.slickfun.scuba.underwater").formatted(Formatting.RED), true);
			return TypedActionResult.fail(stack);
		}

		if (player.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.fail(stack);
		}

		stack.setDamage(0);
		player.getItemCooldownManager().set(this, REFILL_COOLDOWN_TICKS);

		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 1.0F, 0.8F);
		player.getServerWorld().spawnParticles(ParticleTypes.BUBBLE_POP,
				player.getX(), player.getY() + 1.2D, player.getZ(), 15, 0.3D, 0.3D, 0.3D, 0.02D);
		player.sendMessage(Text.translatable("message.slickfun.scuba.refilled").formatted(Formatting.AQUA), true);

		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.scuba_tank", airLeft(stack) / 20, CAPACITY / 20)
				.formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.slickfun.scuba_tank.refill").formatted(Formatting.GRAY));
	}
}
