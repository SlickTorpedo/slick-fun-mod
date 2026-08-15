package com.slickfun.util;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.vehicle.BoatEntity;

/**
 * Removes Pocket Boats once nobody is in them, so the item stays a convenience rather than
 * a way to litter every lake on the server.
 */
public final class BoatManager {
	/** Grace period so the boat is not deleted before the rider has climbed in. */
	private static final int SETTLE_TICKS = 40;

	private static final List<Tracked> BOATS = new ArrayList<>();

	private record Tracked(BoatEntity boat, long spawnedAt) {
	}

	private static long tickCounter;

	private BoatManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BOATS.forEach(tracked -> tracked.boat().discard());
			BOATS.clear();
		});
	}

	public static void track(BoatEntity boat) {
		BOATS.add(new Tracked(boat, tickCounter));
	}

	private static void tick() {
		tickCounter++;

		BOATS.removeIf(tracked -> {
			BoatEntity boat = tracked.boat();

			if (boat.isRemoved()) {
				return true;
			}

			if (tickCounter - tracked.spawnedAt() < SETTLE_TICKS) {
				return false;
			}

			if (!boat.hasPassengers()) {
				boat.discard();
				return true;
			}

			return false;
		});
	}
}
