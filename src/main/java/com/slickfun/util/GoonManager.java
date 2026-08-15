package com.slickfun.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Keeps anyone who drank the goon free of harmful effects.
 *
 * <p>Effects are stripped as they land rather than blocked at the source: there is no event
 * for "about to be given an effect", so the immunity works by clearing anything harmful a few
 * times a second. In practice a wither or poison never survives long enough to tick.
 *
 * <p>Only effects vanilla marks harmful are touched, so beneficial ones you drank yourself are
 * left exactly where they are.
 */
public final class GoonManager {
	private static final int INTERVAL_TICKS = 4;

	private static final Map<UUID, Long> IMMUNE = new HashMap<>();
	private static long tickCounter;

	private GoonManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GoonManager::tick);
	}

	public static void grant(ServerPlayerEntity player, int ticks) {
		IMMUNE.put(player.getUuid(), tickCounter + ticks);
		strip(player);

		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.0F, 0.7F);
		player.getServerWorld().spawnParticles(ParticleTypes.SPIT,
				player.getX(), player.getEyeY(), player.getZ(), 40, 0.4D, 0.4D, 0.4D, 0.03D);
		player.sendMessage(Text.translatable("message.slickfun.goon.drunk", ticks / 20 / 60).formatted(Formatting.WHITE), false);
	}

	public static boolean isImmune(ServerPlayerEntity player) {
		Long until = IMMUNE.get(player.getUuid());
		return until != null && until > tickCounter;
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (IMMUNE.isEmpty() || tickCounter % INTERVAL_TICKS != 0) {
			return;
		}

		IMMUNE.entrySet().removeIf(entry -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

			if (entry.getValue() <= tickCounter) {
				if (player != null) {
					player.sendMessage(Text.translatable("message.slickfun.goon.worn_off").formatted(Formatting.GRAY), true);
				}

				return true;
			}

			if (player != null) {
				strip(player);
			}

			return false;
		});
	}

	private static void strip(ServerPlayerEntity player) {
		// A copy first - removing from the live view while iterating it would throw.
		List<StatusEffectInstance> active = List.copyOf(player.getStatusEffects());

		for (StatusEffectInstance effect : active) {
			if (effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
				player.removeStatusEffect(effect.getEffectType());
			}
		}
	}
}
