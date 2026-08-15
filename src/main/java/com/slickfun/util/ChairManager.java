package com.slickfun.util;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Sitting is riding an invisible armour stand. The stand is tagged {@code slickfun_seat} so
 * that if one is ever orphaned - a crash while someone is seated - it can be swept up with
 * {@code /kill @e[tag=slickfun_seat]}.
 */
public final class ChairManager {
	public static final String SEAT_TAG = "slickfun_seat";

	private static final List<ArmorStandEntity> SEATS = new ArrayList<>();

	private ChairManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SEATS.forEach(ArmorStandEntity::discard);
			SEATS.clear();
		});
	}

	public static boolean sit(ServerPlayerEntity player, ServerWorld world, double x, double y, double z, float yaw) {
		ArmorStandEntity seat = EntityType.ARMOR_STAND.create(world);

		if (seat == null) {
			return false;
		}

		seat.refreshPositionAndAngles(x, y, z, yaw, 0.0F);
		seat.setInvisible(true);
		seat.setNoGravity(true);
		seat.setInvulnerable(true);
		seat.setSilent(true);
		seat.addCommandTag(SEAT_TAG);

		if (!world.spawnEntity(seat)) {
			return false;
		}

		if (!player.startRiding(seat, true)) {
			seat.discard();
			return false;
		}

		SEATS.add(seat);
		return true;
	}

	private static void tick() {
		SEATS.removeIf(seat -> {
			if (seat.isRemoved()) {
				return true;
			}

			if (!seat.hasPassengers()) {
				seat.discard();
				return true;
			}

			return false;
		});
	}
}
