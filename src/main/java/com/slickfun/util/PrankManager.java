package com.slickfun.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.slickfun.item.PrankItems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/** Keeps the Hot Potato's fuse burning while it changes hands. */
public final class PrankManager {
	public static final int FUSE_TICKS = 20 * 15;

	private static final Map<UUID, Long> FUSES = new HashMap<>();
	private static long tickCounter;

	private PrankManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PrankManager::tick);
	}

	/**
	 * Hands the potato over. The fuse belongs to the potato, not to whoever lit it, so passing
	 * it on does not buy the catcher a fresh fifteen seconds.
	 */
	public static void passPotato(ServerPlayerEntity thrower, ServerPlayerEntity catcher, ItemStack stack, Hand hand) {
		if (thrower == catcher) {
			return;
		}

		Long burning = FUSES.remove(thrower.getUuid());
		long expiry = burning != null ? burning : tickCounter + FUSE_TICKS;

		ItemStack potato = stack.split(1);

		if (stack.isEmpty()) {
			thrower.setStackInHand(hand, ItemStack.EMPTY);
		}

		if (!catcher.getInventory().insertStack(potato)) {
			catcher.dropItem(potato, false);
		}

		FUSES.put(catcher.getUuid(), expiry);

		ServerWorld world = catcher.getServerWorld();
		world.playSound(null, catcher.getX(), catcher.getY(), catcher.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 0.6F);

		catcher.sendMessage(Text.translatable("message.slickfun.potato.caught",
				thrower.getName(), Math.max(1, (expiry - tickCounter) / 20)).formatted(Formatting.RED, Formatting.BOLD), false);
		thrower.sendMessage(Text.translatable("message.slickfun.potato.passed", catcher.getName())
				.formatted(Formatting.GOLD), true);
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (FUSES.isEmpty() || tickCounter % 5 != 0) {
			return;
		}

		FUSES.entrySet().removeIf(entry -> {
			ServerPlayerEntity holder = server.getPlayerManager().getPlayer(entry.getKey());

			if (holder == null) {
				return false;
			}

			int slot = findPotato(holder);

			// Dropped, stashed in a chest, or died with it - the fuse goes out with it.
			if (slot < 0) {
				return true;
			}

			if (entry.getValue() > tickCounter) {
				fizz(holder);
				return false;
			}

			holder.getInventory().setStack(slot, ItemStack.EMPTY);
			pop(holder);
			return true;
		});
	}

	private static int findPotato(ServerPlayerEntity player) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			if (player.getInventory().getStack(slot).getItem() instanceof PrankItems.HotPotato) {
				return slot;
			}
		}

		return -1;
	}

	private static void fizz(ServerPlayerEntity holder) {
		holder.getServerWorld().spawnParticles(ParticleTypes.SMOKE,
				holder.getX(), holder.getY() + 1.8D, holder.getZ(), 3, 0.2D, 0.1D, 0.2D, 0.01D);
	}

	/** All bark. It shoves and it is loud, but it does not hurt anyone or touch the world. */
	private static void pop(ServerPlayerEntity holder) {
		ServerWorld world = holder.getServerWorld();

		holder.setVelocity(holder.getVelocity().x, 0.7D, holder.getVelocity().z);
		holder.velocityModified = true;
		holder.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false, true));
		holder.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, false, true));

		world.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0F, 1.4F);
		world.spawnParticles(ParticleTypes.EXPLOSION, holder.getX(), holder.getY() + 1.0D, holder.getZ(),
				8, 0.5D, 0.5D, 0.5D, 0.0D);

		MinecraftServer server = holder.getServer();

		if (server != null) {
			server.getPlayerManager().broadcast(Text.translatable("message.slickfun.potato.popped", holder.getName())
					.formatted(Formatting.GOLD), false);
		}
	}
}
