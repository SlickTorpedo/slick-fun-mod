package com.slickfun.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Everything a player has on them: their inventory, plus their trinket slots.
 *
 * <p>Every passive charm asks the same question - "is this on the player" - and the answer
 * should not depend on whether they put it in a backpack or a trinket slot. Routing all of
 * them through here is what keeps those two answers the same, and means a charm added later
 * gets trinket support without anyone remembering to wire it up.
 *
 * <p>The stacks are the real objects, not copies, so a charm that stores state on itself -
 * the Quiver's contents, the Scuba Tank's air - can be written to through this list.
 */
public final class Carried {
	private Carried() {
	}

	public static List<ItemStack> stacks(PlayerEntity player) {
		List<ItemStack> all = new ArrayList<>();

		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (!stack.isEmpty()) {
				all.add(stack);
			}
		}

		all.addAll(TrinketCompat.equippedStacks(player));
		return all;
	}

	public static boolean has(PlayerEntity player, Item item) {
		for (ItemStack stack : stacks(player)) {
			if (stack.isOf(item)) {
				return true;
			}
		}

		return false;
	}
}
