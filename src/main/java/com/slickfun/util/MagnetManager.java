package com.slickfun.util;

import com.slickfun.item.ItemMagnetItem;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Drives every switched-on Item Magnet. Runs on a slow cadence; the pull is smooth regardless. */
public final class MagnetManager {
	private static final double RADIUS = 7.0D;
	private static final double PULL_SPEED = 0.45D;
	private static final int INTERVAL_TICKS = 4;

	private static int tickCounter;

	private MagnetManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MagnetManager::tick);
	}

	private static void tick(MinecraftServer server) {
		if (++tickCounter % INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator() || !hasActiveMagnet(player)) {
				continue;
			}

			Vec3d target = player.getPos().add(0.0D, 0.4D, 0.0D);
			Box area = player.getBoundingBox().expand(RADIUS);

			for (ItemEntity item : player.getServerWorld().getEntitiesByClass(
					ItemEntity.class, area, entity -> !entity.cannotPickup())) {
				attract(item, target);
			}

			for (ExperienceOrbEntity orb : player.getServerWorld().getEntitiesByClass(
					ExperienceOrbEntity.class, area, entity -> true)) {
				attract(orb, target);
			}
		}
	}

	private static void attract(net.minecraft.entity.Entity entity, Vec3d target) {
		Vec3d delta = target.subtract(entity.getPos());
		double distance = delta.length();

		// Close enough that vanilla pickup will grab it this tick anyway.
		if (distance < 0.7D) {
			return;
		}

		entity.setVelocity(delta.normalize().multiply(PULL_SPEED));
		entity.velocityModified = true;
	}

	private static boolean hasActiveMagnet(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);

			if (stack.getItem() instanceof ItemMagnetItem && ItemMagnetItem.isActive(stack)) {
				return true;
			}
		}

		return false;
	}
}
