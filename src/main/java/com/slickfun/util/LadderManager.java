package com.slickfun.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Owns every temporary ladder in the world.
 *
 * <p>The rules exist so this stays a climbing aid rather than a ladder printer: one run per
 * player at a time, it expires on a timer, breaking any rung takes the whole run down, and
 * the rungs are deleted rather than broken so they never drop an item.
 */
public final class LadderManager {
	public static final int MAX_RUNGS = 4;
	public static final int LIFETIME_TICKS = 20 * 45;

	/** How many rungs under the player survive when they start a fresh run. */
	private static final int KEEP_UNDERFOOT = 2;

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static long tickCounter;

	private record Run(RegistryKey<World> dimension, List<BlockPos> rungs, long expiresAt) {
	}

	private LadderManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LadderManager::tick);

		// Breaking a rung takes the run down instead, so nothing ever drops.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!state.isOf(Blocks.LADDER) || !(world instanceof ServerWorld serverWorld)) {
				return true;
			}

			UUID owner = ownerOf(serverWorld, pos);

			if (owner == null) {
				return true;
			}

			clear(serverWorld.getServer(), owner);
			return false;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				clear(server, handler.getPlayer().getUuid());
			}
		});
	}

	/**
	 * Replaces whatever run this player had with a new one, but leaves the rungs directly
	 * under their feet standing - pulling those out from under a climbing player just drops
	 * them. The retained rungs join the new run, so they still expire on its timer.
	 */
	public static void claim(ServerPlayerEntity player, List<BlockPos> rungs) {
		Run previous = RUNS.remove(player.getUuid());
		List<BlockPos> keeping = new ArrayList<>(rungs);

		if (previous != null) {
			int feet = MathHelper.floor(player.getY());
			List<BlockPos> footing = previous.rungs().stream()
					.filter(pos -> pos.getY() <= feet && pos.getY() > feet - KEEP_UNDERFOOT)
					.toList();

			for (BlockPos pos : previous.rungs()) {
				if (!footing.contains(pos) && !keeping.contains(pos)) {
					removeRung(player.getServerWorld(), pos);
				}
			}

			for (BlockPos pos : footing) {
				if (!keeping.contains(pos)) {
					keeping.add(pos);
				}
			}
		}

		RUNS.put(player.getUuid(),
				new Run(player.getServerWorld().getRegistryKey(), List.copyOf(keeping), tickCounter + LIFETIME_TICKS));
	}

	private static UUID ownerOf(ServerWorld world, BlockPos pos) {
		for (Map.Entry<UUID, Run> entry : RUNS.entrySet()) {
			if (entry.getValue().dimension().equals(world.getRegistryKey()) && entry.getValue().rungs().contains(pos)) {
				return entry.getKey();
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

	private static void remove(MinecraftServer server, Run run) {
		ServerWorld world = server.getWorld(run.dimension());

		if (world == null) {
			return;
		}

		run.rungs().forEach(pos -> removeRung(world, pos));
	}

	private static void removeRung(ServerWorld world, BlockPos pos) {
		// Only ours, and only if it is still a ladder - never eat a real one.
		if (world.getBlockState(pos).isOf(Blocks.LADDER)) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (RUNS.isEmpty()) {
			return;
		}

		List<Run> expired = null;
		Iterator<Map.Entry<UUID, Run>> iterator = RUNS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Run> entry = iterator.next();

			if (entry.getValue().expiresAt() <= tickCounter) {
				iterator.remove();

				if (expired == null) {
					expired = new ArrayList<>();
				}

				expired.add(entry.getValue());
			}
		}

		if (expired != null) {
			expired.forEach(run -> remove(server, run));
		}
	}
}
