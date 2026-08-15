package com.slickfun.item;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.world.World;

/** Snaps a storm. Consumed on use, so it can't just be spammed to keep it sunny forever. */
public class WeatherStickItem extends Item {
	private static final int CLEAR_DURATION_TICKS = 20 * 60 * 15;

	public WeatherStickItem(Settings settings) {
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

		ServerWorld serverWorld = player.getServerWorld();

		if (serverWorld.getRegistryKey() != World.OVERWORLD) {
			player.sendMessage(Text.translatable("message.slickfun.weather.wrong_dimension").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		if (!serverWorld.isRaining() && !serverWorld.isThundering()) {
			player.sendMessage(Text.translatable("message.slickfun.weather.already_clear").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		serverWorld.setWeather(CLEAR_DURATION_TICKS, 0, false, false);
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 0.4F, 1.6F);
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				player.getX(), player.getBodyY(1.2D), player.getZ(), 30, 0.4D, 0.6D, 0.4D, 0.05D);

		player.getServer().getPlayerManager().broadcast(
				Text.translatable("message.slickfun.weather.cleared", player.getDisplayName()).formatted(Formatting.AQUA), false);

		if (!player.isCreative()) {
			stack.decrement(1);
		}

		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.weather_stick").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.weather_stick.2").formatted(Formatting.DARK_GRAY));
	}
}
