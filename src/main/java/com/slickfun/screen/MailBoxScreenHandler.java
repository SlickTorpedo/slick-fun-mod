package com.slickfun.screen;

import com.slickfun.block.ChestLikeBlockEntity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * The Mail Box's screen. Identical to a chest for the owner; post-only for everyone else.
 *
 * <p>Every way of removing an item is refused for a visitor rather than only the obvious one:
 * a plain click, a shift click, a number-key swap and a drop key all reach the inventory by
 * different routes.
 */
public class MailBoxScreenHandler extends GenericContainerScreenHandler {
	private static final int MAIL_SLOTS = ChestLikeBlockEntity.SIZE;

	private final boolean owner;

	private MailBoxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, boolean owner) {
		super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
		this.owner = owner;
	}

	public static MailBoxScreenHandler create(int syncId, PlayerInventory playerInventory, Inventory inventory, boolean owner) {
		return new MailBoxScreenHandler(syncId, playerInventory, inventory, owner);
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (this.owner || slotIndex < 0 || slotIndex >= MAIL_SLOTS || isDeposit(slotIndex, actionType)) {
			super.onSlotClick(slotIndex, button, actionType, player);
			return;
		}

		syncState();
	}

	/** True only for the one gesture that adds to the box without taking anything back. */
	private boolean isDeposit(int slotIndex, SlotActionType actionType) {
		if (actionType != SlotActionType.PICKUP) {
			return false;
		}

		ItemStack cursor = getCursorStack();

		if (cursor.isEmpty()) {
			return false;
		}

		ItemStack existing = this.slots.get(slotIndex).getStack();

		// Dropping onto a different item would swap the two, which is a withdrawal.
		return existing.isEmpty() || ItemStack.areItemsAndComponentsEqual(existing, cursor);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		// Shift clicking inside the box is the only direction a visitor must not go.
		if (!this.owner && index < MAIL_SLOTS) {
			return ItemStack.EMPTY;
		}

		return super.quickMove(player, index);
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return true;
	}
}
