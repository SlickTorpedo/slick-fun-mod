package com.slickfun.util;

import java.util.ArrayList;
import java.util.List;

import com.slickfun.item.PortableUtilityItem;
import com.slickfun.registry.ModItems;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Reads and writes the Swiss Army Knife's contents.
 *
 * <p>Every portable utility has one reserved slot, in the order they appear in {@link
 * ModItems#TOOLKIT_ORDER} - the anvil is always in the same place whether or not you have
 * crafted the stonecutter yet. That is also what enforces one-per-kind: there is simply
 * nowhere else for a second anvil to go.
 *
 * <p>Storage is the vanilla {@code minecraft:container} data component, so the contents ride
 * along with the item stack itself - through chests, trades, and handing the knife to
 * someone else - with no extra syncing work.
 */
public final class Toolkit {
	/** Two rows of slots. The utility list must not outgrow it. */
	public static final int SIZE = 18;

	private Toolkit() {
	}

	public static boolean isStorable(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof PortableUtilityItem && slotFor(stack) >= 0;
	}

	/** The one slot this tool belongs in, or -1 if it is not a portable utility. */
	public static int slotFor(ItemStack stack) {
		int index = ModItems.TOOLKIT_ORDER.indexOf(stack.getItem());
		return index < SIZE ? index : -1;
	}

	public static DefaultedList<ItemStack> read(ItemStack knife) {
		DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
		knife.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(stacks);
		return stacks;
	}

	public static void write(ItemStack knife, Inventory inventory) {
		List<ItemStack> stacks = new ArrayList<>(SIZE);

		for (int slot = 0; slot < SIZE; slot++) {
			ItemStack stored = slot < inventory.size() ? inventory.getStack(slot) : ItemStack.EMPTY;
			// Belt and braces: never persist anything that is not a tool in its own slot.
			stacks.add(slotFor(stored) == slot ? stored : ItemStack.EMPTY);
		}

		knife.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
	}

	/** Writes a slot list straight back, used when a nested tool changes its own state. */
	public static void writeAll(ItemStack knife, List<ItemStack> stacks) {
		knife.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
	}
}
