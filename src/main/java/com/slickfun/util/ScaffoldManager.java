package com.slickfun.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Scaffold Wand's floors.
 *
 * <p>Every block carries its own expiry stamped at the moment it was placed, so clicking again
 * cannot extend the life of anything already down - a run-wide timer that got pushed back on
 * each use was what made the platforms seem to last forever.
 *
 * <p>Breaking any one block takes the whole run with it, and the run is capped at three
 * platforms' worth. Both are deliberate: this is a short-lived leg-up, not a bridge.
 */
public final class ScaffoldManager {
	/** Deliberately short. Long enough to cross a gap, not long enough to build on. */
	public static final int LIFETIME_TICKS = 20 * 5;

	/** Three clicks of a 3x3. */
	public static final int MAX_BLOCKS = 27;

	/**
	 * What the platform is made of. Deliberately not scaffolding: an unsupported scaffolding
	 * block turns itself into a falling block and drops an item, which would make the wand a
	 * way to print scaffolding. Glass has no support rules to break.
	 */
	public static final Block PLATFORM = Blocks.LIGHT_BLUE_STAINED_GLASS;

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static long tickCounter;

	/** One block, and the tick it dies on. The stamp never changes once it is set. */
	private record Placed(BlockPos pos, long expiresAt) {
	}

	private record Run(RegistryKey<World> dimension, List<Placed> blocks) {
	}

	private ScaffoldManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ScaffoldManager::tick);

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!state.isOf(PLATFORM) || !(world instanceof ServerWorld serverWorld)) {
				return true;
			}

			UUID owner = ownerOf(serverWorld, pos);

			if (owner == null) {
				return true;
			}

			// Break one, lose the lot.
			clear(serverWorld.getServer(), owner);
			return false;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				clear(server, handler.getPlayer().getUuid());
			}
		});

		// Runs live only in memory, so without this every platform standing at shutdown is
		// stranded in the world with nothing left that knows it was ever temporary.
		ServerLifecycleEvents.SERVER_STOPPING.register(ScaffoldManager::clearAll);
	}

	/** Adds a freshly placed platform to this player's run, each block on its own clock. */
	public static void claim(ServerPlayerEntity player, List<BlockPos> fresh) {
		ServerWorld world = player.getServerWorld();
		Run previous = RUNS.get(player.getUuid());

		if (previous != null && !previous.dimension().equals(world.getRegistryKey())) {
			remove(player.getServer(), previous);
			previous = null;
		}

		List<Placed> blocks = previous == null ? new ArrayList<>() : new ArrayList<>(previous.blocks());
		long expiry = tickCounter + LIFETIME_TICKS;

		for (BlockPos pos : fresh) {
			blocks.removeIf(placed -> placed.pos().equals(pos));
			blocks.add(new Placed(pos.toImmutable(), expiry));
		}

		// Oldest first out of the door once the cap is hit.
		while (blocks.size() > MAX_BLOCKS) {
			removeBlock(world, blocks.remove(0).pos());
		}

		RUNS.put(player.getUuid(), new Run(world.getRegistryKey(), blocks));
	}

	private static UUID ownerOf(ServerWorld world, BlockPos pos) {
		for (Map.Entry<UUID, Run> entry : RUNS.entrySet()) {
			if (!entry.getValue().dimension().equals(world.getRegistryKey())) {
				continue;
			}

			for (Placed placed : entry.getValue().blocks()) {
				if (placed.pos().equals(pos)) {
					return entry.getKey();
				}
			}
		}

		return null;
	}

	public static void clear(MinecraftServer server, UUID owner) {
		Run run = RUNS.remove(owner);

		if (run != null && server != null) {
			remove(server, run);
		}
	}

	private static void clearAll(MinecraftServer server) {
		RUNS.values().forEach(run -> remove(server, run));
		RUNS.clear();
	}

	private static void remove(MinecraftServer server, Run run) {
		ServerWorld world = server == null ? null : server.getWorld(run.dimension());

		if (world == null) {
			return;
		}

		run.blocks().forEach(placed -> removeBlock(world, placed.pos()));
	}

	private static void removeBlock(ServerWorld world, BlockPos pos) {
		// Only ours, and only if it is still the platform block - never eat a real one.
		if (world.getBlockState(pos).isOf(PLATFORM)) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (RUNS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Run>> iterator = RUNS.entrySet().iterator();
		List<Runnable> pending = new ArrayList<>();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Run> entry = iterator.next();
			Run run = entry.getValue();
			ServerWorld world = server.getWorld(run.dimension());
			List<Placed> alive = new ArrayList<>(run.blocks().size());

			for (Placed placed : run.blocks()) {
				if (placed.expiresAt() <= tickCounter) {
					if (world != null) {
						pending.add(() -> removeBlock(world, placed.pos()));
					}
				} else {
					alive.add(placed);
				}
			}

			if (alive.isEmpty()) {
				iterator.remove();
			} else if (alive.size() != run.blocks().size()) {
				entry.setValue(new Run(run.dimension(), alive));
			}
		}

		pending.forEach(Runnable::run);
	}
}
