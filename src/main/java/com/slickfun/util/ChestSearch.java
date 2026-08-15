package com.slickfun.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Finds an item in nearby containers and shows you where it is.
 *
 * <p>Scanning every block in a 60 block radius would mean over a million positions. Instead
 * this walks the block entity list of each nearby chunk, which is a short list of exactly
 * the things that could be containers.
 */
public final class ChestSearch {
	public static final int RADIUS = 60;
	private static final int HIGHLIGHT_SECONDS = 20;
	private static final int MAX_RESULTS = 24;

	private ChestSearch() {
	}

	public record Hit(BlockPos pos, int count, double distance) {
	}

	public static List<Hit> find(ServerWorld world, Vec3d from, Item wanted) {
		List<Hit> hits = new ArrayList<>();
		ChunkPos centre = new ChunkPos(BlockPos.ofFloored(from));
		int chunkRange = (RADIUS >> 4) + 1;

		for (int cx = centre.x - chunkRange; cx <= centre.x + chunkRange; cx++) {
			for (int cz = centre.z - chunkRange; cz <= centre.z + chunkRange; cz++) {
				// Only chunks already in memory - never force-load half the world to search.
				WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz, false);

				if (chunk == null) {
					continue;
				}

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof Inventory container)) {
						continue;
					}

					double distance = Math.sqrt(blockEntity.getPos().getSquaredDistance(from));

					if (distance > RADIUS) {
						continue;
					}

					int count = countIn(container, wanted);

					if (count > 0) {
						hits.add(new Hit(blockEntity.getPos(), count, distance));
					}
				}
			}
		}

		hits.sort(Comparator.comparingDouble(Hit::distance));
		return hits.size() > MAX_RESULTS ? hits.subList(0, MAX_RESULTS) : hits;
	}

	private static int countIn(Inventory container, Item wanted) {
		int total = 0;

		for (int slot = 0; slot < container.size(); slot++) {
			ItemStack stack = container.getStack(slot);

			if (stack.isOf(wanted)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** Marks every hit with a column of particles for a while. */
	public static void highlight(ServerWorld world, ServerPlayerEntity player, List<Hit> hits) {
		for (int second = 0; second < HIGHLIGHT_SECONDS; second++) {
			ServerScheduler.schedule(second * 10 + 1, () -> {
				if (player.isRemoved()) {
					return;
				}

				for (Hit hit : hits) {
					double x = hit.pos().getX() + 0.5D;
					double z = hit.pos().getZ() + 0.5D;

					for (int height = 0; height < 6; height++) {
						world.spawnParticles(ParticleTypes.END_ROD,
								x, hit.pos().getY() + 1.0D + height * 0.5D, z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
					}

					world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
							x, hit.pos().getY() + 1.2D, z, 3, 0.3D, 0.3D, 0.3D, 0.0D);
				}
			});
		}
	}

	/**
	 * A standing spot in front of a container, preferring the side it faces. Returns null if
	 * there is nowhere safe, rather than dropping someone inside a wall.
	 */
	public static Vec3d landingSpot(ServerWorld world, BlockPos container) {
		BlockState state = world.getBlockState(container);
		List<Direction> tries = new ArrayList<>();

		if (state.contains(HorizontalFacingBlock.FACING)) {
			tries.add(state.get(HorizontalFacingBlock.FACING));
		}

		tries.add(Direction.NORTH);
		tries.add(Direction.SOUTH);
		tries.add(Direction.EAST);
		tries.add(Direction.WEST);

		for (Direction direction : tries) {
			BlockPos feet = container.offset(direction);

			if (standable(world, feet)) {
				return new Vec3d(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
			}

			// The block in front might be solid but the space above it clear.
			if (standable(world, feet.up())) {
				return new Vec3d(feet.getX() + 0.5D, feet.getY() + 1.0D, feet.getZ() + 0.5D);
			}
		}

		return null;
	}

	private static boolean standable(ServerWorld world, BlockPos feet) {
		return world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
				&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
				&& !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty();
	}
}
