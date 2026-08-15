package com.slickfun.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * The Admin Magnet's reach, and the ledger that lets the Magnetic Rewind undo it.
 *
 * <p>Every stack taken is written down against where it came from, which is the only reason a
 * rewind is possible at all - once items are merged into one inventory there is otherwise
 * nothing left to say which chest each one came out of.
 *
 * <p>Items nested inside a shulker box are taken, but recorded against whatever was holding
 * the shulker rather than the shulker itself. Putting them back inside a box that may since
 * have been moved, renamed or emptied is not something that can be done honestly.
 */
public final class MagnetSweep {
	public static final int MIN_RANGE = 10;
	public static final int MAX_RANGE = 100;
	public static final int RANGE_STEP = 10;

	/** How many stacks may be in the air at once before the rest is handed over directly. */
	private static final int MAX_FLIGHTS = 128;

	/** Where one stack came from. Exactly one of the two is set. */
	public record Source(BlockPos container, UUID player) {
	}

	public record Entry(Source source, Item item, int count) {
	}

	public record Ledger(RegistryKey<World> dimension, List<Entry> entries) {
	}

	private static final Map<UUID, Ledger> LAST = new HashMap<>();

	private MagnetSweep() {
	}

	public static Ledger lastFor(ServerPlayerEntity admin) {
		return LAST.get(admin.getUuid());
	}

	public static void forget(ServerPlayerEntity admin) {
		LAST.remove(admin.getUuid());
	}

	// ------------------------------------------------------------------ pulling items in

	/** Empties every container, pack and dropped stack in range of one item. Returns the haul. */
	public static int sweepItem(ServerPlayerEntity admin, Item wanted, int range) {
		ServerWorld world = admin.getServerWorld();
		List<Entry> ledger = new ArrayList<>();
		int haul = 0;

		haul += fromContainers(world, admin.getPos(), range, wanted, ledger);
		haul += fromPlayers(admin, range, wanted, ledger);
		haul += fromGround(world, admin, range, wanted, ledger);

		if (haul > 0) {
			LAST.put(admin.getUuid(), new Ledger(world.getRegistryKey(), ledger));
			deliver(admin, world, wanted, ledger);
		}

		return haul;
	}

	/**
	 * Sends the haul flying in from wherever each stack was taken.
	 *
	 * <p>The ledger already knows every source, so the flights come out of the actual chests
	 * rather than materialising in front of the player. Past {@link #MAX_FLIGHTS} stacks it
	 * stops spawning entities and hands the rest over directly - a sweep for cobblestone
	 * across a hundred blocks would otherwise put thousands of item entities in the air.
	 */
	private static void deliver(ServerPlayerEntity admin, ServerWorld world, Item item, List<Entry> ledger) {
		int flights = 0;

		for (Entry entry : ledger) {
			Vec3d origin = originOf(world, entry.source(), admin);
			int left = entry.count();

			while (left > 0) {
				ItemStack batch = new ItemStack(item, Math.min(item.getMaxCount(), left));
				left -= batch.getCount();

				if (flights < MAX_FLIGHTS) {
					MagnetFlight.launch(world, origin, batch, admin);
					flights++;
				} else if (!admin.getInventory().insertStack(batch)) {
					admin.dropItem(batch, false);
				}
			}
		}
	}

	private static Vec3d originOf(ServerWorld world, Source source, ServerPlayerEntity admin) {
		if (source.container() != null) {
			return Vec3d.ofCenter(source.container());
		}

		if (source.player() != null) {
			ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(source.player());

			if (owner != null) {
				return owner.getPos().add(0.0D, 1.0D, 0.0D);
			}
		}

		return admin.getPos();
	}

	private static int fromContainers(ServerWorld world, Vec3d from, int range, Item wanted, List<Entry> ledger) {
		ChunkPos centre = new ChunkPos(BlockPos.ofFloored(from));
		int chunkRange = (range >> 4) + 1;
		int taken = 0;

		for (int cx = centre.x - chunkRange; cx <= centre.x + chunkRange; cx++) {
			for (int cz = centre.z - chunkRange; cz <= centre.z + chunkRange; cz++) {
				// Loaded chunks only - never drag half the world into memory for a sweep.
				WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz, false);

				if (chunk == null) {
					continue;
				}

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof Inventory container)
							|| Math.sqrt(blockEntity.getPos().getSquaredDistance(from)) > range) {
						continue;
					}

					int got = drain(container, wanted);

					if (got > 0) {
						ledger.add(new Entry(new Source(blockEntity.getPos(), null), wanted, got));
						container.markDirty();
						taken += got;
					}
				}
			}
		}

		return taken;
	}

	private static int fromPlayers(ServerPlayerEntity admin, int range, Item wanted, List<Entry> ledger) {
		int taken = 0;

		for (ServerPlayerEntity other : admin.getServer().getPlayerManager().getPlayerList()) {
			if (other == admin || other.getWorld() != admin.getWorld()
					|| other.getPos().distanceTo(admin.getPos()) > range) {
				continue;
			}

			PlayerInventory inventory = other.getInventory();
			int got = drain(inventory, wanted);

			if (got > 0) {
				ledger.add(new Entry(new Source(null, other.getUuid()), wanted, got));
				taken += got;
			}
		}

		return taken;
	}

	private static int fromGround(ServerWorld world, ServerPlayerEntity admin, int range, Item wanted, List<Entry> ledger) {
		Box area = admin.getBoundingBox().expand(range);
		int taken = 0;

		for (ItemEntity loose : world.getEntitiesByClass(ItemEntity.class, area,
				item -> item.isAlive() && item.getStack().isOf(wanted))) {
			int got = loose.getStack().getCount();
			// Recorded against where it was lying, so a rewind puts it back on that spot.
			ledger.add(new Entry(new Source(loose.getBlockPos(), null), wanted, got));
			loose.discard();
			taken += got;
		}

		return taken;
	}

	/** Takes every matching item out of an inventory, including out of any shulkers inside it. */
	private static int drain(Inventory inventory, Item wanted) {
		int taken = 0;

		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);

			if (stack.isEmpty()) {
				continue;
			}

			if (stack.isOf(wanted)) {
				taken += stack.getCount();
				inventory.setStack(slot, ItemStack.EMPTY);
				continue;
			}

			taken += drainNested(stack, wanted);
		}

		return taken;
	}

	private static int drainNested(ItemStack holder, Item wanted) {
		ContainerComponent contents = holder.get(DataComponentTypes.CONTAINER);

		if (contents == null) {
			return 0;
		}

		List<ItemStack> kept = new ArrayList<>();
		int taken = 0;

		for (ItemStack inside : contents.streamNonEmpty().toList()) {
			if (inside.isOf(wanted)) {
				taken += inside.getCount();
			} else {
				kept.add(inside.copy());
			}
		}

		if (taken > 0) {
			holder.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(kept));
		}

		return taken;
	}

	// ------------------------------------------------------------------ pulling entities in

	/**
	 * Hauls matching entities to the admin, straight through whatever is between them. Handed
	 * to {@link MagnetDrag}, which marches them in over a few seconds. Not undoable.
	 */
	public static int sweepEntities(ServerPlayerEntity admin, Predicate<Entity> matches, int range) {
		ServerWorld world = admin.getServerWorld();
		Box area = admin.getBoundingBox().expand(range);
		int pulled = 0;

		for (Entity entity : world.getOtherEntities(admin, area, matches::test)) {
			MagnetDrag.haul(entity, admin);
			pulled++;
		}

		return pulled;
	}

	// ------------------------------------------------------------------ putting it back

	/**
	 * Reverses the last sweep: takes the items back off the admin and out of the world, and
	 * returns them where they came from. Anything whose home is gone is dropped at the spot.
	 */
	public static int rewind(ServerPlayerEntity admin) {
		Ledger ledger = LAST.remove(admin.getUuid());

		if (ledger == null) {
			return -1;
		}

		ServerWorld world = admin.getServer().getWorld(ledger.dimension());

		if (world == null) {
			return -1;
		}

		int returned = 0;

		for (Entry entry : ledger.entries()) {
			int available = reclaim(admin, world, entry.item(), entry.count());

			if (available <= 0) {
				continue;
			}

			returned += available;
			restore(world, entry, available);
		}

		return returned;
	}

	/** Pulls the items back out of the admin, then off the floor around them if needed. */
	private static int reclaim(ServerPlayerEntity admin, ServerWorld world, Item item, int wanted) {
		int got = 0;

		for (int slot = 0; slot < admin.getInventory().size() && got < wanted; slot++) {
			ItemStack stack = admin.getInventory().getStack(slot);

			if (!stack.isOf(item)) {
				continue;
			}

			int moved = Math.min(wanted - got, stack.getCount());
			stack.decrement(moved);
			got += moved;

			if (stack.isEmpty()) {
				admin.getInventory().setStack(slot, ItemStack.EMPTY);
			}
		}

		if (got < wanted) {
			Box area = admin.getBoundingBox().expand(16.0D);

			for (ItemEntity loose : world.getEntitiesByClass(ItemEntity.class, area,
					dropped -> dropped.isAlive() && dropped.getStack().isOf(item))) {
				if (got >= wanted) {
					break;
				}

				int moved = Math.min(wanted - got, loose.getStack().getCount());
				loose.getStack().decrement(moved);
				got += moved;

				if (loose.getStack().isEmpty()) {
					loose.discard();
				}
			}
		}

		return got;
	}

	private static void restore(ServerWorld world, Entry entry, int count) {
		Source source = entry.source();

		if (source.player() != null) {
			ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(source.player());

			if (owner != null) {
				insertOrDrop(owner, entry.item(), count);
				return;
			}
		}

		BlockPos pos = source.container();

		if (pos != null && world.getBlockEntity(pos) instanceof Inventory container) {
			int left = fill(container, entry.item(), count);
			container.markDirty();

			if (left > 0) {
				scatter(world, pos, entry.item(), left);
			}

			return;
		}

		scatter(world, pos == null ? BlockPos.ofFloored(0, 64, 0) : pos, entry.item(), count);
	}

	private static void insertOrDrop(ServerPlayerEntity player, Item item, int count) {
		int left = count;

		while (left > 0) {
			ItemStack batch = new ItemStack(item, Math.min(item.getMaxCount(), left));
			left -= batch.getCount();

			if (!player.getInventory().insertStack(batch)) {
				player.dropItem(batch, false);
			}
		}
	}

	/** Returns whatever would not fit. */
	private static int fill(Inventory container, Item item, int count) {
		int left = count;

		for (int slot = 0; slot < container.size() && left > 0; slot++) {
			ItemStack stack = container.getStack(slot);

			if (stack.isEmpty()) {
				ItemStack batch = new ItemStack(item, Math.min(item.getMaxCount(), left));
				container.setStack(slot, batch);
				left -= batch.getCount();
			} else if (stack.isOf(item)) {
				int room = Math.min(stack.getMaxCount(), container.getMaxCountPerStack()) - stack.getCount();
				int moved = Math.min(room, left);

				if (moved > 0) {
					stack.increment(moved);
					left -= moved;
				}
			}
		}

		return left;
	}

	private static void scatter(ServerWorld world, BlockPos pos, Item item, int count) {
		int left = count;

		while (left > 0) {
			ItemStack batch = new ItemStack(item, Math.min(item.getMaxCount(), left));
			left -= batch.getCount();
			net.minecraft.util.ItemScatterer.spawn(world, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, batch);
		}
	}
}
