package com.slickfun.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Items in the air, on their way to whoever pulled them.
 *
 * <p>The Admin Magnet drops real item entities at the chest it emptied and flies them in,
 * rather than moving numbers straight into an inventory. It is slower and it is showier, and
 * that is the point - you can see where your stuff came from and who took it.
 *
 * <p>Gravity is off during the flight so they track cleanly instead of arcing into the floor,
 * and goes back on at the end so they land and settle at the player's feet like anything else.
 */
public final class MagnetFlight {
	/** A generous ceiling; anything still airborne after this lands where it is. */
	private static final int MAX_FLIGHT_TICKS = 20 * 8;

	private static final double ARRIVE_DISTANCE = 1.4D;
	private static final double SPEED = 0.85D;

	private record Flight(ItemEntity item, UUID target, long expiresAt) {
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static long tickCounter;

	private MagnetFlight() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MagnetFlight::tick);

		// An item in flight is flagged unpickupable. If the server stops before it lands that
		// flag is all that survives, and the stack is stranded on the floor forever.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FLIGHTS.forEach(flight -> land(flight.item()));
			FLIGHTS.clear();
		});
	}

	public static void launch(ServerWorld world, Vec3d from, ItemStack stack, ServerPlayerEntity to) {
		ItemEntity item = new ItemEntity(world, from.x, from.y + 0.5D, from.z, stack);

		// Nobody picks it up mid-flight, including the person it is flying to.
		item.setPickupDelay(Short.MAX_VALUE);
		item.setNoGravity(true);
		item.setVelocity(Vec3d.ZERO);
		world.spawnEntity(item);

		world.spawnParticles(ParticleTypes.ENCHANT, from.x, from.y + 0.8D, from.z, 12, 0.3D, 0.3D, 0.3D, 0.2D);
		FLIGHTS.add(new Flight(item, to.getUuid(), tickCounter + MAX_FLIGHT_TICKS));
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (FLIGHTS.isEmpty()) {
			return;
		}

		Iterator<Flight> iterator = FLIGHTS.iterator();

		while (iterator.hasNext()) {
			Flight flight = iterator.next();
			ItemEntity item = flight.item();

			if (item.isRemoved()) {
				iterator.remove();
				continue;
			}

			ServerPlayerEntity target = server.getPlayerManager().getPlayer(flight.target());

			// Dropped the moment the trip stops making sense, rather than left hanging.
			if (target == null || target.isRemoved() || target.getWorld() != item.getWorld()
					|| flight.expiresAt() <= tickCounter) {
				land(item);
				iterator.remove();
				continue;
			}

			Vec3d toFeet = target.getPos().add(0.0D, 0.3D, 0.0D).subtract(item.getPos());

			if (toFeet.length() <= ARRIVE_DISTANCE) {
				land(item);
				iterator.remove();
				continue;
			}

			item.setVelocity(toFeet.normalize().multiply(SPEED));
			item.velocityModified = true;
		}
	}

	/** Hands the item back to normal physics so it falls, settles and can be picked up. */
	private static void land(ItemEntity item) {
		item.setNoGravity(false);
		item.setVelocity(item.getVelocity().multiply(0.2D));
		// A beat on the floor before it is collected, so the landing is actually visible.
		item.setPickupDelay(20);
	}
}
