package com.slickfun.util;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The two things the Cat Collar does that the vanilla flee goal cannot.
 *
 * <p>Fleeing is pathfinding, so a creeper that is cornered, already adjacent, or mid-fuse can
 * still end up in your face. This shoves any that get within {@link #PERSONAL_SPACE} blocks
 * and makes sure the blast cannot hurt the wearer even if one does go off.
 */
public final class CatCharmManager {
	/** How close a creeper may get before it is pushed off. */
	private static final double PERSONAL_SPACE = 2.5D;
	private static final double SHOVE_STRENGTH = 0.55D;
	private static final int INTERVAL_TICKS = 2;

	private static int tickCounter;

	private CatCharmManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CatCharmManager::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CatCharmManager::allowDamage);
	}

	/** @return false to cancel the damage entirely. */
	private static boolean allowDamage(net.minecraft.entity.LivingEntity entity,
			net.minecraft.entity.damage.DamageSource source, float amount) {
		if (!source.isIn(DamageTypeTags.IS_EXPLOSION) || !CatCharm.isWornBy(entity)) {
			return true;
		}

		// Only creeper blasts - the collar is not blanket explosion immunity.
		return !(source.getSource() instanceof CreeperEntity) && !(source.getAttacker() instanceof CreeperEntity);
	}

	private static void tick(MinecraftServer server) {
		if (++tickCounter % INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator() || !CatCharm.isWornBy(player)) {
				continue;
			}

			ServerWorld world = player.getServerWorld();
			Box space = player.getBoundingBox().expand(PERSONAL_SPACE);
			List<CreeperEntity> tooClose = world.getEntitiesByClass(CreeperEntity.class, space, CreeperEntity::isAlive);

			for (CreeperEntity creeper : tooClose) {
				shove(world, player, creeper);
			}
		}
	}

	private static void shove(ServerWorld world, ServerPlayerEntity player, CreeperEntity creeper) {
		Vec3d away = creeper.getPos().subtract(player.getPos());

		// Straight overhead or exactly on top: pick any direction rather than divide by zero.
		if (away.horizontalLengthSquared() < 1.0E-4D) {
			away = new Vec3d(world.getRandom().nextDouble() - 0.5D, 0.0D, world.getRandom().nextDouble() - 0.5D);
		}

		Vec3d push = away.multiply(1.0D, 0.0D, 1.0D).normalize().multiply(SHOVE_STRENGTH);
		creeper.setVelocity(push.x, 0.25D, push.z);
		creeper.velocityModified = true;
		creeper.fallDistance = 0.0F;

		if (tickCounter % 10 == 0) {
			world.spawnParticles(ParticleTypes.CLOUD,
					creeper.getX(), creeper.getBodyY(0.5D), creeper.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.02D);
			world.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
					SoundEvents.ENTITY_CAT_HISS, SoundCategory.NEUTRAL, 0.4F, 1.4F);
		}
	}
}
