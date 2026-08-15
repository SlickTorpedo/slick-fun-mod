package com.slickfun.util;

import com.slickfun.item.BulkStorageItems;
import com.slickfun.registry.ModComponents;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Counts and spends building material, from the pack and from bulk containers alike.
 *
 * <p>An Insanely Large Storage full of stone is the obvious thing to build from, and having to
 * shuttle stacks out of it by hand would defeat the point of holding sixty-nine thousand of
 * something. Loose stacks are spent first so the container stays as the reserve.
 */
public final class BlockSupply {
	private BlockSupply() {
	}

	public static int available(ServerPlayerEntity player, Item needed) {
		int total = 0;

		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (stack.isEmpty()) {
				continue;
			}

			if (stack.isOf(needed)) {
				total += stack.getCount();
			} else {
				total += inBulk(stack, needed);
			}
		}

		return total;
	}

	/** Takes as much as it can find, loose stacks first. Returns how much it actually got. */
	public static int consume(ServerPlayerEntity player, Item needed, int wanted) {
		int taken = takeLoose(player, needed, wanted);

		if (taken < wanted) {
			taken += takeFromBulk(player, needed, wanted - taken);
		}

		return taken;
	}

	/** Puts material back, into the pack if it fits and on the floor if it does not. */
	public static void refund(ServerPlayerEntity player, Item item, int count) {
		int left = count;

		while (left > 0) {
			ItemStack batch = new ItemStack(item, Math.min(item.getMaxCount(), left));
			left -= batch.getCount();

			if (!player.getInventory().insertStack(batch)) {
				player.dropItem(batch, false);
			}
		}
	}

	private static int takeLoose(ServerPlayerEntity player, Item needed, int wanted) {
		int taken = 0;

		for (int slot = 0; slot < player.getInventory().size() && taken < wanted; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (!stack.isOf(needed)) {
				continue;
			}

			int moved = Math.min(wanted - taken, stack.getCount());
			stack.decrement(moved);
			taken += moved;

			if (stack.isEmpty()) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
			}
		}

		return taken;
	}

	private static int takeFromBulk(ServerPlayerEntity player, Item needed, int wanted) {
		int taken = 0;

		for (int slot = 0; slot < player.getInventory().size() && taken < wanted; slot++) {
			ItemStack container = player.getInventory().getStack(slot);

			if (!(container.getItem() instanceof BulkStorageItems.Bulk)) {
				continue;
			}

			BulkStore store = BulkStorageItems.Bulk.storeOf(container);

			if (store.isEmpty() || !store.sample().isOf(needed)) {
				continue;
			}

			int moved = Math.min(wanted - taken, store.count());
			container.set(ModComponents.BULK_STORE, store.less(moved));
			taken += moved;
		}

		return taken;
	}

	private static int inBulk(ItemStack container, Item needed) {
		if (!(container.getItem() instanceof BulkStorageItems.Bulk)) {
			return 0;
		}

		BulkStore store = BulkStorageItems.Bulk.storeOf(container);
		return !store.isEmpty() && store.sample().isOf(needed) ? store.count() : 0;
	}
}
