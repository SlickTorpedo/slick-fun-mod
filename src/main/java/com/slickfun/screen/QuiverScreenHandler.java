package com.slickfun.screen;

import java.util.ArrayList;
import java.util.List;

import com.slickfun.item.FinalTools;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;

/**
 * The Quiver's nine slots.
 *
 * <p>Arrows only, and the check is repeated at every door into the inventory - direct clicks,
 * shift clicks and number-key swaps each get there by a different route, and a guard on only
 * one of them leaves the other two open.
 */
public class QuiverScreenHandler extends GenericContainerScreenHandler {
	private static final int SLOTS = FinalTools.Quiver.SLOTS;

	private final SimpleInventory arrows;
	private final Hand hand;

	private QuiverScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory arrows, Hand hand) {
		super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, arrows, 1);
		this.arrows = arrows;
		this.hand = hand;
	}

	public static void open(ServerPlayerEntity player, Hand hand) {
		SimpleInventory arrows = read(player.getStackInHand(hand));

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, ignored) -> new QuiverScreenHandler(syncId, inventory, arrows, hand),
				Text.translatable("container.slickfun.quiver")));
	}

	public static SimpleInventory read(ItemStack quiver) {
		SimpleInventory inventory = new SimpleInventory(SLOTS);
		DefaultedList<ItemStack> stored = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);

		quiver.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(stored);

		for (int slot = 0; slot < SLOTS; slot++) {
			inventory.setStack(slot, stored.get(slot));
		}

		return inventory;
	}

	public static void write(ItemStack quiver, SimpleInventory inventory) {
		List<ItemStack> stacks = new ArrayList<>(SLOTS);

		for (int slot = 0; slot < SLOTS; slot++) {
			stacks.add(inventory.getStack(slot).copy());
		}

		quiver.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
	}

	public static boolean isArrow(ItemStack stack) {
		return stack.getItem() instanceof ArrowItem;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (slotIndex >= 0 && slotIndex < SLOTS && !accepts(slotIndex, button, actionType, player)) {
			return;
		}

		// The quiver itself must not end up inside the quiver, or closing the screen loses both.
		if (slotIndex >= 0 && slotIndex >= SLOTS && this.slots.get(slotIndex).getStack() == held(player)) {
			return;
		}

		super.onSlotClick(slotIndex, button, actionType, player);
		save(player);
	}

	/** Whether this click would put something that is not an arrow into a quiver slot. */
	private boolean accepts(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		return switch (actionType) {
			case PICKUP, QUICK_MOVE, PICKUP_ALL, THROW, CLONE ->
					getCursorStack().isEmpty() || isArrow(getCursorStack());
			case SWAP -> {
				ItemStack incoming = button == 40
						? player.getInventory().offHand.get(0)
						: player.getInventory().getStack(button);
				yield incoming.isEmpty() || isArrow(incoming);
			}
			default -> true;
		};
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = this.slots.get(index);

		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}

		ItemStack original = slot.getStack();
		ItemStack before = original.copy();

		if (index < SLOTS) {
			if (!insertItem(original, SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (!isArrow(original) || original == held(player) || !insertItem(original, 0, SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (original.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}

		save(player);
		return original.getCount() == before.getCount() ? ItemStack.EMPTY : before;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != this.arrows || isArrow(stack);
	}

	private ItemStack held(PlayerEntity player) {
		return player.getStackInHand(this.hand);
	}

	private void save(PlayerEntity player) {
		ItemStack quiver = held(player);

		if (quiver.getItem() instanceof FinalTools.Quiver) {
			write(quiver, this.arrows);
		}
	}

	@Override
	public void onClosed(PlayerEntity player) {
		save(player);
		super.onClosed(player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return held(player).getItem() instanceof FinalTools.Quiver;
	}
}
