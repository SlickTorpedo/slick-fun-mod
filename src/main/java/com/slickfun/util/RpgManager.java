package com.slickfun.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Watches RPG rounds in flight and sets off the illusion where they land.
 *
 * <p>The round's own impact handler cannot be used without a custom projectile class, so its
 * last known position is recorded every tick instead; the moment the entity disappears, that
 * is where it hit. The alternative - a bespoke entity type - would need a client renderer to
 * be visible at all.
 */
public final class RpgManager {
	/** A rocket-sized hole. The self destruct button is the one that takes the horizon. */
	private static final int BLAST_RADIUS = 7;

	/** The grenade's illusion, smaller than the rocket's. */
	private static final int GRENADE_RADIUS = 4;

	/** The redstone round genuinely removes this much. Deliberately tiny. */
	private static final int REAL_RADIUS = 3;
	private static final float REAL_DAMAGE = 12.0F;

	private static final int MAX_BLOCKS = 4000;
	private static final int RESTORE_TICKS = 20 * 5;
	private static final int VIEWER_RANGE = 160;
	private static final int MAX_FLIGHT_TICKS = 20 * 10;
	private static final double SHOVE_RANGE = 7.0D;

	/** What a round does when it lands. */
	public enum Payload {
		/** The original RPG: a big crater that is not there. */
		ILLUSION,
		/** A grenade: the same trick, smaller, plus a real thump. */
		GRENADE,
		/** The redstone RPG: genuinely breaks blocks and genuinely hurts. */
		REAL
	}

	private record Round(SnowballEntity entity, ServerPlayerEntity shooter, Payload payload, Vec3d lastSeen,
			long expiresAt) {
	}

	private static final List<Round> ROUNDS = new ArrayList<>();
	private static long tickCounter;

	private RpgManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RpgManager::tick);
	}

	public static void track(SnowballEntity round, ServerPlayerEntity shooter) {
		track(round, shooter, Payload.ILLUSION);
	}

	public static void track(SnowballEntity round, ServerPlayerEntity shooter, Payload payload) {
		ROUNDS.add(new Round(round, shooter, payload, round.getPos(), tickCounter + MAX_FLIGHT_TICKS));
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (ROUNDS.isEmpty()) {
			return;
		}

		List<Round> moved = new ArrayList<>();
		Iterator<Round> iterator = ROUNDS.iterator();

		while (iterator.hasNext()) {
			Round round = iterator.next();

			// Gone means it hit something, and the last position we saw is the impact point.
			if (round.entity().isRemoved()) {
				detonate(round);
				iterator.remove();
				continue;
			}

			if (round.expiresAt() <= tickCounter) {
				round.entity().discard();
				detonate(round);
				iterator.remove();
				continue;
			}

			moved.add(new Round(round.entity(), round.shooter(), round.payload(), round.entity().getPos(), round.expiresAt()));
			iterator.remove();
		}

		ROUNDS.addAll(moved);
	}

	private static void detonate(Round round) {
		if (!(round.entity().getWorld() instanceof ServerWorld world)) {
			return;
		}

		Vec3d at = round.lastSeen();
		BlockPos centre = BlockPos.ofFloored(at);

		// The redstone round is the one that is not pretending, so it takes a different path
		// entirely and never touches the illusion code.
		if (round.payload() == Payload.REAL) {
			RealDemolition.detonate(world, round.shooter(), at, REAL_RADIUS, REAL_DAMAGE, 0.8D);
			return;
		}

		int radius = round.payload() == Payload.GRENADE ? GRENADE_RADIUS : BLAST_RADIUS;

		// Always shown to everyone in range - a rocket nobody else can see is not much of a rocket.
		FakeDemolition.blast(world, centre, radius, null, RESTORE_TICKS, MAX_BLOCKS, VIEWER_RANGE);
		FakeDemolition.barrage(world, centre, radius, 4, 8);

		// Knockback only. No block damage, and nothing here takes a hit point off anyone.
		for (Entity caught : world.getOtherEntities(null, new Box(at, at).expand(SHOVE_RANGE),
				entity -> entity instanceof LivingEntity && entity.isAlive())) {
			Vec3d away = caught.getPos().subtract(at);
			double distance = away.length();

			if (distance < 1.0E-3D) {
				continue;
			}

			double force = Math.max(0.2D, 1.1D - distance / SHOVE_RANGE);
			Vec3d shove = away.multiply(1.0D / distance).multiply(force);

			caught.setVelocity(shove.x, Math.max(0.35D, shove.y), shove.z);
			caught.velocityModified = true;
			caught.fallDistance = 0.0F;
		}
	}
}
