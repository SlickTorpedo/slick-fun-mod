package com.slickfun.screen;

import com.slickfun.item.PortableUtilityItem;
import com.slickfun.item.SwissArmyKnifeItem;
import com.slickfun.util.ServerScheduler;
import com.slickfun.util.ToolHost;
import com.slickfun.util.Toolkit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * The Swiss Army Knife's screen: one row, one reserved slot per tool.
 *
 * <ul>
 *   <li>plain click a stored tool - use it (this screen closes and the tool's opens)
 *   <li>shift click a stored tool - take it back out
 *   <li>click a tool from your cursor, or shift click one in your inventory - store it in
 *       its own slot
 * </ul>
 */
public class ToolkitScreenHandler extends GenericContainerScreenHandler {
	private static final int TOOLKIT_SLOTS = Toolkit.SIZE;

	private final SimpleInventory contents;
	private final Hand hand;

	public ToolkitScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory contents, Hand hand) {
		super(ScreenHandlerType.GENERIC_9X2, syncId, playerInventory, contents, 2);
		this.contents = contents;
		this.hand = hand;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		// Dragging spreads one stack over several slots, which reserved slots cannot express.
		if (actionType == SlotActionType.QUICK_CRAFT) {
			syncState();
			return;
		}

		if (slotIndex >= 0 && slotIndex < TOOLKIT_SLOTS) {
			handleToolkitClick(slotIndex, actionType, player);
			return;
		}

		if (slotIndex >= TOOLKIT_SLOTS && actionType == SlotActionType.QUICK_MOVE) {
			handleShiftFromInventory(slotIndex, player);
			return;
		}

		super.onSlotClick(slotIndex, button, actionType, player);
	}

	private void handleToolkitClick(int slotIndex, SlotActionType actionType, PlayerEntity player) {
		ItemStack stored = this.contents.getStack(slotIndex);

		if (actionType == SlotActionType.QUICK_MOVE) {
			if (!stored.isEmpty()) {
				takeOut(slotIndex, stored, player);
			}

			syncState();
			return;
		}

		ItemStack cursor = this.getCursorStack();

		if (cursor.isEmpty()) {
			if (!stored.isEmpty()) {
				activate(slotIndex, stored, player);
			}
		} else {
			store(cursor, slotIndex, player);
		}

		syncState();
	}

	/** Puts a tool from the cursor away, but only into the slot that belongs to it. */
	private void store(ItemStack cursor, int clickedSlot, PlayerEntity player) {
		int home = Toolkit.slotFor(cursor);

		if (home < 0) {
			player.sendMessage(Text.translatable("message.slickfun.knife.not_a_tool").formatted(Formatting.GRAY), true);
			return;
		}

		if (home != clickedSlot) {
			player.sendMessage(Text.translatable("message.slickfun.knife.wrong_slot", cursor.getName())
					.formatted(Formatting.GRAY), true);
			return;
		}

		if (!this.contents.getStack(home).isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.knife.duplicate").formatted(Formatting.GRAY), true);
			return;
		}

		this.contents.setStack(home, cursor.copyWithCount(1));
		this.setCursorStack(ItemStack.EMPTY);
		save(player);
		click(player, 1.4F);
	}

	private void handleShiftFromInventory(int slotIndex, PlayerEntity player) {
		Slot source = this.slots.get(slotIndex);
		ItemStack stack = source.getStack();
		int home = Toolkit.slotFor(stack);

		if (home < 0) {
			syncState();
			return;
		}

		if (!this.contents.getStack(home).isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.knife.duplicate").formatted(Formatting.GRAY), true);
			syncState();
			return;
		}

		this.contents.setStack(home, source.takeStack(1));
		save(player);
		click(player, 1.4F);
		syncState();
	}

	private void takeOut(int slotIndex, ItemStack stored, PlayerEntity player) {
		ItemStack removed = stored.copy();
		this.contents.setStack(slotIndex, ItemStack.EMPTY);

		if (!player.getInventory().insertStack(removed)) {
			player.dropItem(removed, false);
		}

		save(player);
		click(player, 0.9F);
	}

	private void activate(int slotIndex, ItemStack stored, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)
				|| !(stored.getItem() instanceof PortableUtilityItem utility)) {
			return;
		}

		// Opening a screen from inside a click handler would swap the handler out from under
		// the packet being processed right now, so let this tick finish first. By then this
		// screen has closed and saved, so the tool is addressed through the knife.
		ServerScheduler.schedule(1, () -> {
			if (!serverPlayer.isRemoved()) {
				utility.openFor(serverPlayer, ToolHost.inKnife(serverPlayer, this.hand, slotIndex));
			}
		});
	}

	/** Persists the contents back onto the knife the player is holding. */
	private void save(PlayerEntity player) {
		ItemStack knife = player.getStackInHand(this.hand);

		if (knife.getItem() instanceof SwissArmyKnifeItem) {
			Toolkit.write(knife, this.contents);
		}
	}

	private void click(PlayerEntity player, float pitch) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, pitch);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		// All shift-click behaviour is handled in onSlotClick above.
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.id >= TOOLKIT_SLOTS || Toolkit.slotFor(stack) == slot.id;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		save(player);
		super.onClosed(player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getStackInHand(this.hand).getItem() instanceof SwissArmyKnifeItem;
	}
}
