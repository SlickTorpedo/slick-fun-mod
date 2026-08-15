package com.slickfun.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * The plumbing every chest-shaped block in this mod repeats: a 27 slot inventory that saves
 * itself, works with hoppers, and reports a comparator signal.
 *
 * <p>Subclasses decide the policy - what a hopper is allowed to pull back out, and what the
 * screen looks like - and inherit the rest.
 */
public abstract class ChestLikeBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
	public static final int SIZE = 27;

	private static final int[] ALL_SLOTS = java.util.stream.IntStream.range(0, SIZE).toArray();

	protected final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

	protected ChestLikeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public DefaultedList<ItemStack> contents() {
		return this.inventory;
	}

	/** Tries to take in one stack, returning what would not fit. */
	public ItemStack accept(ItemStack incoming) {
		ItemStack remaining = incoming;

		for (int slot = 0; slot < SIZE && !remaining.isEmpty(); slot++) {
			ItemStack existing = this.inventory.get(slot);

			if (existing.isEmpty()) {
				this.inventory.set(slot, remaining);
				markDirty();
				return ItemStack.EMPTY;
			}

			if (ItemStack.areItemsAndComponentsEqual(existing, remaining)) {
				int room = Math.min(existing.getMaxCount(), getMaxCountPerStack()) - existing.getCount();
				int moved = Math.min(room, remaining.getCount());

				if (moved > 0) {
					existing.increment(moved);
					remaining.decrement(moved);
					markDirty();
				}
			}
		}

		return remaining;
	}

	// ------------------------------------------------------------------ inventory

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.inventory.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.inventory.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack taken = Inventories.splitStack(this.inventory, slot, amount);

		if (!taken.isEmpty()) {
			markDirty();
		}

		return taken;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(this.inventory, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		this.inventory.set(slot, stack);
		stack.capCount(getMaxCountPerStack());
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.world != null
				&& this.world.getBlockEntity(this.pos) == this
				&& player.squaredDistanceTo(Vec3d.ofCenter(this.pos)) <= 64.0D;
	}

	@Override
	public void clear() {
		this.inventory.clear();
	}

	// ------------------------------------------------------------------ hoppers

	@Override
	public int[] getAvailableSlots(Direction side) {
		return ALL_SLOTS;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction direction) {
		return isValid(slot, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction direction) {
		return hoppersMayEmpty();
	}

	/**
	 * Whether a hopper is allowed to pull items back out. Overridden to false wherever the
	 * block's whole point is that only one person may take from it - otherwise a hopper
	 * placed underneath is a way around the check.
	 */
	protected boolean hoppersMayEmpty() {
		return true;
	}

	// ------------------------------------------------------------------ persistence

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.inventory.clear();
		Inventories.readNbt(nbt, this.inventory, registries);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, this.inventory, registries);
	}
}
