package com.slickfun.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.ChunkSection;

/**
 * Craters, fake TNT and anything else that should look like the world came apart without any
 * of it happening.
 *
 * <p>The server's world is never touched. Chosen viewers are sent block updates describing a
 * scorched hole, which their clients believe and render; the server's copy is untouched, so
 * nothing drops, nothing falls, no redstone fires, the fire cannot spread, and anyone who was
 * not sent the illusion sees a perfectly normal build.
 *
 * <p>Updates go out batched per 16x16x16 section rather than one packet per block. That is
 * what makes a blast the size of a render distance possible at all - hundreds of thousands of
 * individual packets would stall the connection long before the client drew any of it.
 *
 * <p>Putting it back re-reads from the live world rather than a snapshot, so anything that
 * genuinely changed meanwhile is shown as it really is instead of stamped over.
 */
public final class FakeDemolition {
	/** Where the hollow ends and the scorched shell begins, as a fraction of the radius. */
	private static final double HOLLOW = 0.84D;

	/** Craters do not need to reach bedrock; nobody can see that far down anyway. */
	public static final int MAX_DEPTH = 34;

	private FakeDemolition() {
	}

	// ------------------------------------------------------------------ the simple case

	/** One-shot crater for a single explosion, shown to everyone in range. */
	public static void blast(ServerWorld world, BlockPos centre, int radius, BlockPos spare,
			int restoreTicks, int maxBlocks, int viewerRange) {
		List<ServerPlayerEntity> viewers = viewersWithin(world, centre, viewerRange);
		Map<BlockPos, BlockState> crater = crater(world, centre, radius, spare, maxBlocks);

		if (crater.isEmpty()) {
			return;
		}

		show(world, viewers, crater);
		spectacle(world, centre, radius);

		ServerScheduler.schedule(restoreTicks, () -> restore(world, viewers, crater.keySet()));
	}

	// ------------------------------------------------------------------ building illusions

	/**
	 * Works out what each block should look like.
	 *
	 * <p>Only ground that is really there gets carved, so the hole follows the terrain instead
	 * of hanging a sphere in the sky. The band at the edge turns to magma and blackstone, and a
	 * scatter of fire sits on top - the three together are what make it read as a blast crater
	 * rather than a chunk of missing world.
	 *
	 * <p>The shape is a squashed ellipsoid rather than a sphere. A crater a hundred blocks deep
	 * costs an enormous amount of work to build and nobody can see the bottom of it.
	 */
	public static Map<BlockPos, BlockState> crater(ServerWorld world, BlockPos centre, int radius,
			BlockPos spare, int maxBlocks) {
		Map<BlockPos, BlockState> illusion = new LinkedHashMap<>();
		Random random = Random.create(centre.asLong());

		int depth = Math.min(radius, MAX_DEPTH);
		double hollow = HOLLOW * HOLLOW;

		for (BlockPos pos : BlockPos.iterate(centre.add(-radius, -depth, -radius), centre.add(radius, depth, radius))) {
			if (illusion.size() >= maxBlocks) {
				break;
			}

			double dx = (double) (pos.getX() - centre.getX()) / radius;
			double dy = (double) (pos.getY() - centre.getY()) / depth;
			double dz = (double) (pos.getZ() - centre.getZ()) / radius;
			double falloff = dx * dx + dy * dy + dz * dz;

			if (falloff > 1.0D || pos.equals(spare) || !world.isInBuildLimit(pos)) {
				continue;
			}

			// Never touch an unloaded chunk - reading one would drag it into memory.
			if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				continue;
			}

			BlockPos at = pos.toImmutable();

			if (!world.getBlockState(at).isAir()) {
				illusion.put(at, falloff < hollow ? Blocks.AIR.getDefaultState() : scorched(random));
			} else if (falloff >= hollow && random.nextInt(11) == 0 && !world.getBlockState(at.down()).isAir()) {
				// Fire only where there is something under it to be burning.
				illusion.put(at, Blocks.FIRE.getDefaultState());
			}
		}

		return illusion;
	}

	/**
	 * Sets the surroundings alight without setting anything alight.
	 *
	 * <p>Only surfaces catch - a block with open air above it turns to magma or lava, and the
	 * space above it fills with fire. Burning the insides of the ground would be invisible and
	 * would cost the whole budget on blocks nobody can see.
	 */
	public static Map<BlockPos, BlockState> inferno(ServerWorld world, BlockPos centre, int radius, int maxBlocks) {
		Map<BlockPos, BlockState> illusion = new LinkedHashMap<>();
		Random random = Random.create(centre.asLong() ^ 0x5EEDFA11L);

		int depth = Math.min(radius, MAX_DEPTH);

		for (BlockPos pos : BlockPos.iterate(centre.add(-radius, -depth, -radius), centre.add(radius, depth, radius))) {
			if (illusion.size() >= maxBlocks) {
				break;
			}

			double dx = (double) (pos.getX() - centre.getX()) / radius;
			double dy = (double) (pos.getY() - centre.getY()) / depth;
			double dz = (double) (pos.getZ() - centre.getZ()) / radius;

			if (dx * dx + dy * dy + dz * dz > 1.0D || !world.isInBuildLimit(pos)) {
				continue;
			}

			if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				continue;
			}

			BlockPos at = pos.toImmutable();
			boolean solid = !world.getBlockState(at).isAir();
			boolean openAbove = world.getBlockState(at.up()).isAir();

			if (solid && openAbove) {
				illusion.put(at, random.nextInt(5) == 0
						? Blocks.LAVA.getDefaultState()
						: Blocks.MAGMA_BLOCK.getDefaultState());
			} else if (!solid && random.nextInt(3) == 0 && !world.getBlockState(at.down()).isAir()) {
				illusion.put(at, Blocks.FIRE.getDefaultState());
			}
		}

		return illusion;
	}

	/** Scatters TNT that is not there, sitting on whatever ground it can find. */
	public static Map<BlockPos, BlockState> scatterTnt(ServerWorld world, BlockPos centre, int radius, int count) {
		Map<BlockPos, BlockState> tnt = new LinkedHashMap<>();
		Random random = Random.create(centre.asLong() ^ 0x7F4A7C15L);

		for (int attempt = 0; attempt < count * 12 && tnt.size() < count; attempt++) {
			int x = centre.getX() + random.nextBetween(-radius, radius);
			int z = centre.getZ() + random.nextBetween(-radius, radius);
			int y = centre.getY() + random.nextBetween(-3, 3);
			BlockPos spot = new BlockPos(x, y, z);

			if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4) || !world.isInBuildLimit(spot)) {
				continue;
			}

			// Standing on something, and not buried inside it.
			if (world.getBlockState(spot).isAir() && !world.getBlockState(spot.down()).isAir()) {
				tnt.put(spot, Blocks.TNT.getDefaultState());
			}
		}

		return tnt;
	}

	// ------------------------------------------------------------------ shipping it out

	public static void show(ServerWorld world, List<ServerPlayerEntity> viewers, Map<BlockPos, BlockState> illusion) {
		send(world, viewers, group(illusion), false);
	}

	public static void restore(ServerWorld world, List<ServerPlayerEntity> viewers, Collection<BlockPos> positions) {
		Map<BlockPos, BlockState> truth = new LinkedHashMap<>();
		positions.forEach(pos -> truth.put(pos, Blocks.AIR.getDefaultState()));

		List<ServerPlayerEntity> remaining = new ArrayList<>();

		for (ServerPlayerEntity viewer : viewers) {
			// Anyone who left resyncs from the real world on their own.
			if (!viewer.isRemoved() && viewer.getWorld() == world) {
				remaining.add(viewer);
			}
		}

		send(world, remaining, group(truth), true);
	}

	public static List<ServerPlayerEntity> viewersWithin(ServerWorld world, BlockPos centre, int range) {
		Vec3d middle = Vec3d.ofCenter(centre);

		return new ArrayList<>(world.getPlayers(player ->
				player.getPos().squaredDistanceTo(middle) <= (double) range * range));
	}

	private static Map<Long, Map<BlockPos, BlockState>> group(Map<BlockPos, BlockState> illusion) {
		Map<Long, Map<BlockPos, BlockState>> bySection = new LinkedHashMap<>();

		for (Map.Entry<BlockPos, BlockState> entry : illusion.entrySet()) {
			long section = ChunkSectionPos.from(entry.getKey()).asLong();
			bySection.computeIfAbsent(section, key -> new HashMap<>()).put(entry.getKey(), entry.getValue());
		}

		return bySection;
	}

	/** One packet per section per viewer. {@code truth} sends the live world instead. */
	private static void send(ServerWorld world, List<ServerPlayerEntity> viewers,
			Map<Long, Map<BlockPos, BlockState>> bySection, boolean truth) {
		// Building the packets is the expensive half; skip it when nobody would receive them.
		if (viewers.isEmpty()) {
			return;
		}

		for (Map.Entry<Long, Map<BlockPos, BlockState>> entry : bySection.entrySet()) {
			ChunkSectionPos sectionPos = ChunkSectionPos.from(entry.getKey());
			ChunkDeltaUpdateS2CPacket packet = packet(world, sectionPos, entry.getValue(), truth);

			for (ServerPlayerEntity viewer : viewers) {
				viewer.networkHandler.sendPacket(packet);
			}
		}
	}

	/**
	 * Builds the batched update.
	 *
	 * <p>The packet reads its states out of a chunk section, so a throwaway one is filled with
	 * just the blocks being changed. It is never attached to the world - it exists only long
	 * enough to be serialised.
	 */
	private static ChunkDeltaUpdateS2CPacket packet(ServerWorld world, ChunkSectionPos sectionPos,
			Map<BlockPos, BlockState> states, boolean truth) {
		ChunkSection section = new ChunkSection(world.getRegistryManager().get(RegistryKeys.BIOME));
		ShortSet positions = new ShortOpenHashSet();

		for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
			BlockPos pos = entry.getKey();
			BlockState state = truth ? world.getBlockState(pos) : entry.getValue();

			section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
			positions.add(ChunkSectionPos.packLocal(pos));
		}

		return new ChunkDeltaUpdateS2CPacket(sectionPos, positions, section);
	}

	private static BlockState scorched(Random random) {
		return switch (random.nextInt(6)) {
			case 0, 1 -> Blocks.MAGMA_BLOCK.getDefaultState();
			case 2, 3 -> Blocks.BLACKSTONE.getDefaultState();
			case 4 -> Blocks.NETHERRACK.getDefaultState();
			default -> Blocks.BASALT.getDefaultState();
		};
	}

	// ------------------------------------------------------------------ noise and smoke

	public static void spectacle(ServerWorld world, BlockPos centre, int radius) {
		Vec3d middle = Vec3d.ofCenter(centre);
		int rings = Math.min(40, 8 + radius / 2);

		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, middle.x, middle.y, middle.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

		for (int i = 0; i < rings; i++) {
			double angle = i * (2 * Math.PI / rings);
			double spread = radius * 0.55D;
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
					middle.x + Math.cos(angle) * spread, middle.y + world.getRandom().nextDouble() * radius * 0.4D,
					middle.z + Math.sin(angle) * spread, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}

		for (int layer = 0; layer < 4; layer++) {
			world.playSound(null, centre, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS,
					4.0F, 0.5F + layer * 0.15F);
		}

		world.playSound(null, centre, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 4.0F, 0.6F);
	}

	/** Explosions going off all over the place for a while, rather than one and done. */
	public static void barrage(ServerWorld world, BlockPos centre, int radius, int waves, int spacingTicks) {
		for (int wave = 0; wave < waves; wave++) {
			ServerScheduler.schedule(wave * spacingTicks, () -> {
				Vec3d middle = Vec3d.ofCenter(centre);

				for (int burst = 0; burst < 6; burst++) {
					double angle = world.getRandom().nextDouble() * Math.PI * 2;
					double distance = world.getRandom().nextDouble() * radius;
					double x = middle.x + Math.cos(angle) * distance;
					double z = middle.z + Math.sin(angle) * distance;
					double y = middle.y + (world.getRandom().nextDouble() - 0.3D) * radius * 0.5D;

					world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
					world.playSound(null, BlockPos.ofFloored(x, y, z), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
							SoundCategory.BLOCKS, 4.0F, 0.4F + world.getRandom().nextFloat() * 0.8F);
				}

				world.spawnParticles(ParticleTypes.LARGE_SMOKE, middle.x, middle.y + radius * 0.15D, middle.z,
						150, radius * 0.4D, radius * 0.2D, radius * 0.4D, 0.05D);
			});
		}
	}
}
