package com.slickfun.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Hauls mobs and players to the magnet, through anything in the way.
 *
 * <p>Two different mechanisms are needed, because collision is decided in two different
 * places. A mob is moved by the server, so clearing its {@code noClip} flag is enough to walk
 * it through walls. A player's collision is run by their own client, which will refuse a
 * velocity that pushes them into a block and snap them back - so players are stepped along
 * with outright teleports instead, one per tick, which the client always accepts.
 *
 * <p>Either way the pull is a fixed march toward the magnet rather than a shove, so a wall
 * cannot stall it and nothing arrives at lethal speed.
 */
public final class MagnetDrag {
	private static final int MAX_TICKS = 20 * 6;
	private static final double SPEED = 0.55D;
	private static final double ARRIVE_DISTANCE = 2.0D;

	private record Drag(Entity entity, UUID target, long expiresAt) {
	}

	private static final List<Drag> DRAGS = new ArrayList<>();
	private static long tickCounter;

	private MagnetDrag() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MagnetDrag::tick);

		// noClip left set on a mob would let it wander through the world forever.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DRAGS.forEach(drag -> release(drag.entity()));
			DRAGS.clear();
		});
	}

	public static void haul(Entity entity, ServerPlayerEntity to) {
		DRAGS.removeIf(drag -> drag.entity() == entity);
		DRAGS.add(new Drag(entity, to.getUuid(), tickCounter + MAX_TICKS));
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (DRAGS.isEmpty()) {
			return;
		}

		Iterator<Drag> iterator = DRAGS.iterator();

		while (iterator.hasNext()) {
			Drag drag = iterator.next();
			Entity entity = drag.entity();

			if (entity.isRemoved() || !entity.isAlive()) {
				iterator.remove();
				continue;
			}

			ServerPlayerEntity target = server.getPlayerManager().getPlayer(drag.target());

			if (target == null || target.isRemoved() || target.getWorld() != entity.getWorld()
					|| drag.expiresAt() <= tickCounter) {
				release(entity);
				iterator.remove();
				continue;
			}

			Vec3d toTarget = target.getPos().add(0.0D, 0.2D, 0.0D).subtract(entity.getPos());

			if (toTarget.length() <= ARRIVE_DISTANCE) {
				release(entity);
				iterator.remove();
				continue;
			}

			step(entity, entity.getPos().add(toTarget.normalize().multiply(SPEED)));

			if (entity.getWorld() instanceof ServerWorld world && tickCounter % 2 == 0) {
				world.spawnParticles(ParticleTypes.ENCHANT,
						entity.getX(), entity.getY() + 0.8D, entity.getZ(), 4, 0.2D, 0.3D, 0.2D, 0.1D);
			}
		}
	}

	private static void step(Entity entity, Vec3d to) {
		// Nothing being dragged should land hurt, so the counter is cleared the whole way.
		entity.fallDistance = 0.0F;

		if (entity instanceof ServerPlayerEntity player) {
			player.networkHandler.requestTeleport(to.x, to.y, to.z, player.getYaw(), player.getPitch());
			return;
		}

		entity.noClip = true;
		entity.setVelocity(Vec3d.ZERO);
		entity.setPosition(to.x, to.y, to.z);
	}

	private static void release(Entity entity) {
		entity.noClip = false;
		entity.setVelocity(Vec3d.ZERO);
		entity.velocityModified = true;
		entity.fallDistance = 0.0F;
	}
}
