package com.slickfun.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.slickfun.registry.ModDamageTypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Tracks players who are currently sitting in a portable hot tub. The tub bubbles away for
 * {@link #SOAK_TICKS} ticks, cooking the occupant, and then finishes the job.
 */
public final class HotTubManager {
	/** How long the bubbling lasts before the tub claims its victim. */
	public static final int SOAK_TICKS = 60;

	private static final Map<UUID, Integer> SOAKING = new HashMap<>();

	private HotTubManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HotTubManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				SOAKING.remove(handler.getPlayer().getUuid());
			}
		});
	}

	public static boolean isSoaking(ServerPlayerEntity player) {
		return SOAKING.containsKey(player.getUuid());
	}

	public static void startSoak(ServerPlayerEntity player) {
		SOAKING.put(player.getUuid(), 0);

		ServerWorld world = player.getServerWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();

		world.playSound(null, x, y, z, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, 0.8F);
		world.playSound(null, x, y, z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.PLAYERS, 1.4F, 1.0F);
		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.9F, 1.1F);

		player.sendMessage(Text.translatable("message.slickfun.hot_tub.enter").formatted(Formatting.AQUA), true);
	}

	private static void tick(MinecraftServer server) {
		if (SOAKING.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Integer>> iterator = SOAKING.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

			if (player == null || player.isRemoved() || !player.isAlive()) {
				iterator.remove();
				continue;
			}

			int elapsed = entry.getValue() + 1;
			entry.setValue(elapsed);

			ServerWorld world = player.getServerWorld();
			double x = player.getX();
			double y = player.getY();
			double z = player.getZ();

			// The tub itself: churning water plus steam rolling off the top.
			world.spawnParticles(ParticleTypes.BUBBLE_POP, x, y + 0.35D, z, 10, 0.45D, 0.35D, 0.45D, 0.02D);
			world.spawnParticles(ParticleTypes.BUBBLE, x, y + 0.15D, z, 8, 0.4D, 0.2D, 0.4D, 0.01D);
			world.spawnParticles(ParticleTypes.SPLASH, x, y + 0.8D, z, 6, 0.4D, 0.2D, 0.4D, 0.05D);
			world.spawnParticles(ParticleTypes.CLOUD, x, y + 1.1D, z, 3, 0.35D, 0.25D, 0.35D, 0.01D);

			if (elapsed % 6 == 0) {
				float pitch = 0.8F + world.getRandom().nextFloat() * 0.6F;
				world.playSound(null, x, y, z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.PLAYERS, 0.7F, pitch);
			}

			if (elapsed % 20 == 0) {
				world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.6F, 1.3F);
				world.playSound(null, x, y, z, SoundEvents.BLOCK_LAVA_POP, SoundCategory.PLAYERS, 0.5F, 1.6F);
			}

			// Visibly cooking, but the kill comes from the hot tub damage type below so the
			// death message stays on brand.
			player.setFireTicks(80);

			if (elapsed >= SOAK_TICKS) {
				iterator.remove();
				player.setFireTicks(0);

				world.playSound(null, x, y, z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 0.6F);
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.8D, z, 40, 0.5D, 0.6D, 0.5D, 0.05D);

				player.damage(ModDamageTypes.source(world, ModDamageTypes.HOT_TUB), Float.MAX_VALUE);
			}
		}
	}
}
