package com.slickfun.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The shared bullet: a ray walked in short steps, checking blocks and entities at the same
 * point along it.
 *
 * <p>Walking it by hand rather than using the entity raycast helper is what lets the block
 * check and the target check happen together - that is what stops a shot passing cleanly
 * through a wall to hit whoever is standing behind it.
 *
 * <p>Everything alive is a valid target except the shooter, spectators and creative players.
 * Piercing weapons keep going after a hit; the rest stop at the first thing they touch.
 */
public final class Ballistics {
	private Ballistics() {
	}

	/**
	 * @param maxTargets how many things one shot may hit before it stops. 1 for most guns.
	 * @param trail      drawn at every step, or null for an invisible shot.
	 */
	public static List<LivingEntity> fire(ServerWorld level, ServerPlayerEntity shooter, Vec3d from, Vec3d aim,
			double range, double step, double radius, int maxTargets, ParticleEffect trail) {
		List<LivingEntity> hits = new ArrayList<>();

		for (double travelled = 0.4D; travelled < range; travelled += step) {
			Vec3d point = from.add(aim.multiply(travelled));

			if (trail != null && travelled > 0.8D) {
				level.spawnParticles(trail, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}

			BlockPos block = BlockPos.ofFloored(point);

			if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
				return hits;
			}

			Box around = new Box(point, point).expand(radius);

			for (LivingEntity candidate : level.getEntitiesByClass(LivingEntity.class, around,
					other -> other != shooter && other.isAlive() && !other.isSpectator() && !hitAlready(hits, other))) {
				hits.add(candidate);

				if (hits.size() >= maxTargets) {
					return hits;
				}
			}
		}

		return hits;
	}

	private static boolean hitAlready(List<LivingEntity> hits, LivingEntity candidate) {
		for (LivingEntity hit : hits) {
			if (hit == candidate) {
				return true;
			}
		}

		return false;
	}

	/** Nudges an aim vector off true, for weapons that are not meant to be accurate. */
	public static Vec3d spread(ServerWorld level, Vec3d aim, double amount) {
		if (amount <= 0.0D) {
			return aim;
		}

		return aim.add(
				(level.getRandom().nextDouble() - 0.5D) * amount,
				(level.getRandom().nextDouble() - 0.5D) * amount,
				(level.getRandom().nextDouble() - 0.5D) * amount).normalize();
	}

	/** Applies a hit, bypassing the usual half-second of mercy so rapid fire actually lands. */
	public static void hurt(ServerWorld level, ServerPlayerEntity shooter, LivingEntity target, float damage) {
		// Without this every shot after the first inside 10 ticks is silently ignored.
		target.timeUntilRegen = 0;
		target.damage(level.getDamageSources().playerAttack(shooter), damage);

		level.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
				target.getX(), target.getBodyY(0.6D), target.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.0D);
	}
}
