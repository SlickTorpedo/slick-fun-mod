package com.slickfun.util;

import java.util.List;

import com.slickfun.item.BulkStorageItems;
import com.slickfun.registry.ModComponents;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/**
 * Drives every Insanely Large Storage that has been fed ender pearls.
 *
 * <p>It only ever collects what the container is already holding. An empty one does nothing at
 * all until it has been loaded by hand once - otherwise the first thing you happened to pick
 * up would decide, permanently, what your enormous container is for.
 */
public final class AutoStorageManager {
	private static final int INTERVAL_TICKS = 10;

	/** How far it reaches for loose items on the ground. */
	private static final double PICKUP_RADIUS = 8.0D;

	private static int tickCounter;

	private AutoStorageManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AutoStorageManager::tick);
	}

	private static void tick(MinecraftServer server) {
		if (++tickCounter % INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}

			for (int slot = 0; slot < player.getInventory().size(); slot++) {
				ItemStack container = player.getInventory().getStack(slot);

				if (container.getItem() instanceof BulkStorageItems.InsanelyLargeStorage
						&& BulkStorageItems.InsanelyLargeStorage.isCollecting(container)) {
					collect(player, container, slot);
				}
			}
		}
	}

	private static void collect(ServerPlayerEntity player, ItemStack container, int containerSlot) {
		BulkStore store = BulkStorageItems.Bulk.storeOf(container);

		// Nothing to match against yet, so there is nothing it can safely take.
		if (store.isEmpty()) {
			return;
		}

		int room = BulkStorageItems.InsanelyLargeStorage.CAPACITY - store.count();

		if (room <= 0) {
			return;
		}

		int taken = fromInventory(player, store, containerSlot, room);
		taken += fromGround(player, store, room - taken);

		if (taken == 0) {
			return;
		}

		container.set(ModComponents.BULK_STORE, store.with(store.sample(), taken,
				BulkStorageItems.InsanelyLargeStorage.CAPACITY));

		player.getServerWorld().spawnParticles(ParticleTypes.PORTAL,
				player.getX(), player.getY() + 1.0D, player.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.02D);
	}

	private static int fromInventory(ServerPlayerEntity player, BulkStore store, int containerSlot, int room) {
		int taken = 0;

		for (int slot = 0; slot < player.getInventory().size() && taken < room; slot++) {
			if (slot == containerSlot) {
				continue;
			}

			ItemStack candidate = player.getInventory().getStack(slot);

			// Never swallow a container, including a different one of its own kind.
			if (candidate.isEmpty() || candidate.getItem() instanceof BulkStorageItems.Bulk
					|| !ItemStack.areItemsAndComponentsEqual(store.sample(), candidate)) {
				continue;
			}

			int moved = Math.min(room - taken, candidate.getCount());
			candidate.decrement(moved);
			taken += moved;

			if (candidate.isEmpty()) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
			}
		}

		return taken;
	}

	private static int fromGround(ServerPlayerEntity player, BulkStore store, int room) {
		if (room <= 0) {
			return 0;
		}

		ServerWorld world = player.getServerWorld();
		Box area = player.getBoundingBox().expand(PICKUP_RADIUS);
		int taken = 0;

		List<ItemEntity> loose = world.getEntitiesByClass(ItemEntity.class, area,
				item -> item.isAlive() && !item.cannotPickup()
						&& ItemStack.areItemsAndComponentsEqual(store.sample(), item.getStack()));

		for (ItemEntity item : loose) {
			if (taken >= room) {
				break;
			}

			int moved = Math.min(room - taken, item.getStack().getCount());
			item.getStack().decrement(moved);
			taken += moved;

			world.spawnParticles(ParticleTypes.PORTAL,
					item.getX(), item.getY() + 0.3D, item.getZ(), 5, 0.2D, 0.2D, 0.2D, 0.05D);

			if (item.getStack().isEmpty()) {
				item.discard();
			}
		}

		return taken;
	}
}
