package com.slickfun.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Cone-based entity targeting. A plain raycast is unforgiving at range, so instead we take
 * everything in a box in front of the player and keep whatever is closest to the crosshair.
 */
public final class TargetHelper {
	/** cos(~14 degrees) - how far off the crosshair an entity may be and still count. */
	private static final double CONE_DOT = 0.97D;

	private TargetHelper() {
	}

	public static Entity findTarget(PlayerEntity user, double range) {
		Vec3d eye = user.getEyePos();
		Vec3d look = user.getRotationVec(1.0F).normalize();
		Box search = user.getBoundingBox().stretch(look.multiply(range)).expand(2.0D);

		Entity best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (Entity candidate : user.getWorld().getOtherEntities(user, search, TargetHelper::isTargetable)) {
			Vec3d toTarget = candidate.getBoundingBox().getCenter().subtract(eye);
			double distance = toTarget.length();

			if (distance < 0.1D || distance > range) {
				continue;
			}

			double alignment = toTarget.normalize().dotProduct(look);

			if (alignment < CONE_DOT) {
				continue;
			}

			// Prefer things that are dead-on, then things that are near.
			double score = alignment - (distance / range) * 0.02D;

			if (score > bestScore) {
				bestScore = score;
				best = candidate;
			}
		}

		return best;
	}

	private static boolean isTargetable(Entity entity) {
		return entity.isAlive() && !entity.isSpectator() && !entity.isRemoved();
	}

	/** Draws a dotted line of particles from the player's hand to the target. */
	public static Vec3d beamOrigin(PlayerEntity user) {
		return user.getEyePos().add(user.getRotationVec(1.0F).multiply(0.6D)).subtract(0.0D, 0.2D, 0.0D);
	}
}
