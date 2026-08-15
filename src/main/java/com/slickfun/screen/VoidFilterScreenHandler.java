package com.slickfun.screen;

import com.slickfun.item.CharmTools;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;

/**
 * The Void Filter's list, as a screen.
 *
 * <p>Click anything in your own inventory to add it to the list; click an entry in the top
 * row to take it off again. Nothing is ever picked up or moved - the top row holds copies,
 * so there is no way to lose an item to this screen.
 */
public class VoidFilterScreenHandler extends GenericContainerScreenHandler {
	private static final int FILTER_SLOTS = CharmTools.VoidFilter.SLOTS;

	private final SimpleInventory entries;
	private final Hand hand;

	private VoidFilterScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory entries, Hand hand) {
		super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, entries, 1);
		this.entries = entries;
		this.hand = hand;
	}

	public static void open(ServerPlayerEntity player, ItemStack filter, Hand hand) {
		SimpleInventory entries = new SimpleInventory(FILTER_SLOTS);
		DefaultedList<ItemStack> stored = CharmTools.VoidFilter.filterOf(filter);

		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			entries.setStack(slot, stored.get(slot));
		}

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, ignored) -> new VoidFilterScreenHandler(syncId, inventory, entries, hand),
				Text.translatable("container.slickfun.void_filter")));
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (slotIndex < 0) {
			syncState();
			return;
		}

		if (slotIndex < FILTER_SLOTS) {
			removeEntry(slotIndex, player);
		} else {
			addEntry(slotIndex, player);
		}

		syncState();
	}

	private void removeEntry(int slotIndex, PlayerEntity player) {
		if (this.entries.getStack(slotIndex).isEmpty()) {
			return;
		}

		ItemStack removed = this.entries.getStack(slotIndex).copy();
		this.entries.setStack(slotIndex, ItemStack.EMPTY);
		save(player);

		player.sendMessage(Text.translatable("message.slickfun.filter.removed", removed.getName())
				.formatted(Formatting.GRAY), true);
		click(player, 0.8F);
	}

	private void addEntry(int slotIndex, PlayerEntity player) {
		Slot source = this.slots.get(slotIndex);
		ItemStack sample = source.getStack();

		if (sample.isEmpty()) {
			return;
		}

		// Filtering the filter would delete it the moment you closed this screen.
		if (sample.getItem() instanceof CharmTools.VoidFilter) {
			player.sendMessage(Text.translatable("message.slickfun.filter.not_itself").formatted(Formatting.GRAY), true);
			return;
		}

		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			if (this.entries.getStack(slot).isOf(sample.getItem())) {
				player.sendMessage(Text.translatable("message.slickfun.filter.already", sample.getName())
						.formatted(Formatting.GRAY), true);
				return;
			}
		}

		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			if (this.entries.getStack(slot).isEmpty()) {
				// A copy, so the player keeps the item they clicked.
				this.entries.setStack(slot, new ItemStack(sample.getItem()));
				save(player);
				player.sendMessage(Text.translatable("message.slickfun.filter.added", sample.getName())
						.formatted(Formatting.RED), true);
				click(player, 1.4F);
				return;
			}
		}

		player.sendMessage(Text.translatable("message.slickfun.filter.full", FILTER_SLOTS).formatted(Formatting.GRAY), true);
	}

	private void save(PlayerEntity player) {
		ItemStack filter = player.getStackInHand(this.hand);

		if (!(filter.getItem() instanceof CharmTools.VoidFilter)) {
			return;
		}

		java.util.List<ItemStack> stacks = new java.util.ArrayList<>(FILTER_SLOTS);

		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			stacks.add(this.entries.getStack(slot).copy());
		}

		filter.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
	}

	private static void click(PlayerEntity player, float pitch) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, pitch);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return false;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		save(player);
		this.entries.clear();
		super.onClosed(player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getStackInHand(this.hand).getItem() instanceof CharmTools.VoidFilter;
	}
}
