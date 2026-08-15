package com.slickfun.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * A blast that genuinely removes blocks, unlike everything else in this mod.
 *
 * <p>The rules on what it will not touch are deliberately strict, and it is better for it to
 * refuse too much than to eat someone's base:
 *
 * <ul>
 *   <li>anything holding items - any block entity that is an {@link Inventory}, which covers
 *       chests, barrels, shulkers, hoppers, droppers, furnaces and this mod's own containers
 *   <li>anything with a blast resistance of 100 or more - obsidian, ancient debris, enchanting
 *       tables, and every reinforced block above them
 *   <li>anything with negative hardness - bedrock, barriers, the end portal frame
 *   <li>fluids, which would otherwise leave a hole for the sea to pour into
 * </ul>
 *
 * <p>Blocks that do go are broken properly rather than deleted, so they drop as items and can
 * be put back.
 */
public final class RealDemolition {
	/** At or above this, a block is treated as blast-proof and left alone. */
	private static final float BLASTPROOF = 100.0F;

	private RealDemolition() {
	}

	/**
	 * Takes a bite out of the world at {@code centre}.
	 *
	 * @return how many blocks were actually removed.
	 */
	public static int detonate(ServerWorld world, ServerPlayerEntity shooter, Vec3d at, int radius,
			float damage, double shove) {
		BlockPos centre = BlockPos.ofFloored(at);
		List<BlockPos> doomed = new ArrayList<>();
		double squared = (double) radius * radius;

		for (BlockPos pos : BlockPos.iterate(centre.add(-radius, -radius, -radius), centre.add(radius, radius, radius))) {
			if (pos.getSquaredDistance(centre) <= squared && breakable(world, pos)) {
				doomed.add(pos.toImmutable());
			}
		}

		// Collected first, then broken - breaking while iterating would let gravity blocks
		// fall into positions already checked.
		doomed.forEach(pos -> world.breakBlock(pos, true));

		hurt(world, shooter, at, radius, damage, shove);
		spectacle(world, at, radius);

		return doomed.size();
	}

	/** Every reason to leave a block alone, in one place. */
	public static boolean breakable(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);

		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}

		// Bedrock and friends report a negative hardness, meaning unbreakable.
		if (state.getHardness(world, pos) < 0.0F) {
			return false;
		}

		if (state.getBlock().getBlastResistance() >= BLASTPROOF) {
			return false;
		}

		// The important one: never destroy anything that might be holding someone's things.
		BlockEntity blockEntity = world.getBlockEntity(pos);

		return !(blockEntity instanceof Inventory);
	}

	private static void hurt(ServerWorld world, ServerPlayerEntity shooter, Vec3d at, int radius,
			float damage, double shove) {
		Box area = new Box(at, at).expand(radius + 2.0D);

		for (LivingEntity caught : world.getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {
			double distance = caught.getPos().distanceTo(at);
			double falloff = Math.max(0.0D, 1.0D - distance / (radius + 2.0D));

			if (falloff <= 0.0D) {
				continue;
			}

			if (caught != shooter) {
				caught.timeUntilRegen = 0;
				caught.damage(world.getDamageSources().playerAttack(shooter), (float) (damage * falloff));
			}

			Vec3d away = caught.getPos().subtract(at);

			if (away.lengthSquared() > 1.0E-4D) {
				Vec3d push = away.normalize().multiply(shove * falloff);
				caught.setVelocity(push.x, Math.max(0.3D * falloff, push.y), push.z);
				caught.velocityModified = true;
			}
		}
	}

	private static void spectacle(ServerWorld world, Vec3d at, int radius) {
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z,
				60, radius * 0.5D, radius * 0.5D, radius * 0.5D, 0.05D);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
				SoundCategory.BLOCKS, 3.0F, 1.0F);
	}
}
