package com.slickfun.item;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Sleep without a bed. One use, then it's gone - the cost is the whole balance.
 *
 * <p>Sets your respawn point where you stand, clears your phantom timer, and if it is
 * actually night, rolls the world over to morning and stops the rain.
 */
public class PocketBedrollItem extends Item {
	private static final int COOLDOWN_TICKS = 100;

	/** The window in which a vanilla bed is usable. */
	private static final long NIGHT_START = 12542L;
	private static final long NIGHT_END = 23459L;

	public PocketBedrollItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		if (!(user instanceof ServerPlayerEntity player) || player.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.fail(stack);
		}

		ServerWorld serverWorld = player.getServerWorld();

		// Play it straight: in a dimension where beds explode, so does this.
		if (!serverWorld.getDimension().bedWorks()) {
			if (!player.isCreative()) {
				stack.decrement(1);
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			player.sendMessage(Text.translatable("message.slickfun.bedroll.boom").formatted(Formatting.RED), true);
			serverWorld.createExplosion(player, player.getX(), player.getBodyY(0.5D), player.getZ(),
					5.0F, World.ExplosionSourceType.BLOCK);
			return TypedActionResult.success(stack, false);
		}

		// Same guard a bed gives you: you only move your spawn if you meant to.
		if (player.isSneaking()) {
			player.setSpawnPoint(serverWorld.getRegistryKey(), player.getBlockPos(), player.getYaw(), true, false);
			player.sendMessage(Text.translatable("message.slickfun.bedroll.spawn_set").formatted(Formatting.GREEN), true);
		} else {
			player.sendMessage(Text.translatable("message.slickfun.bedroll.slept").formatted(Formatting.GRAY), true);
		}

		// No more phantoms, same as waking up in a bed.
		player.resetStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));

		boolean hasDayCycle = serverWorld.getRegistryKey() == World.OVERWORLD;

		if (hasDayCycle && isNight(serverWorld) && serverWorld.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
			long time = serverWorld.getTimeOfDay() + 24000L;
			serverWorld.setTimeOfDay(time - time % 24000L);

			if (serverWorld.isRaining()) {
				serverWorld.setWeather(6000, 0, false, false);
			}

			player.getServer().getPlayerManager().broadcast(
					Text.translatable("message.slickfun.bedroll.night_skipped", player.getDisplayName())
							.formatted(Formatting.AQUA), false);
		}

		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.6F, 0.7F);
		serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
				player.getX(), player.getBodyY(1.0D), player.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.0D);

		player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		player.incrementStat(Stats.USED.getOrCreateStat(this));

		if (!player.isCreative()) {
			stack.decrement(1);
		}

		return TypedActionResult.success(stack, false);
	}

	private static boolean isNight(ServerWorld world) {
		long time = world.getTimeOfDay() % 24000L;
		return time >= NIGHT_START && time <= NIGHT_END;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.pocket_bedroll.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.pocket_bedroll.2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.pocket_bedroll.3").formatted(Formatting.DARK_GRAY));
	}
}
