package com.slickfun.screen;

import com.slickfun.item.BulkStorageItems;
import com.slickfun.registry.ModComponents;
import com.slickfun.util.BulkStore;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

/**
 * The Insanely Large Storage as a chest, paged.
 *
 * <p>The slots are a picture of a number, not an inventory. Every click is intercepted and
 * turned into arithmetic on the stored total, then the whole page is redrawn from it.
 *
 * <p>That indirection is not decoration. An earlier version let vanilla move stacks between
 * the visible slots and counted them afterwards, which quietly capped the container at one
 * page: once forty-five slots each held sixty-four, there was nowhere for vanilla to put
 * anything else, so it silently refused. Depositing has to add to the total rather than to a
 * slot, and the slots are only ever a rendering of what the total already is.
 */
public class BulkStorageScreenHandler extends GenericContainerScreenHandler {
	private static final int ROWS = 6;
	private static final int TOTAL_SLOTS = ROWS * 9;
	private static final int NAV_ROW_START = TOTAL_SLOTS - 9;
	private static final int PER_PAGE = NAV_ROW_START;
	private static final int PREV_SLOT = NAV_ROW_START;
	private static final int NEXT_SLOT = TOTAL_SLOTS - 1;

	/** The auto-collect switch, two along from the page arrow. */
	private static final int TOGGLE_SLOT = NAV_ROW_START + 2;

	private final SimpleInventory display;
	private final Hand hand;
	private final int capacity;
	private final PlayerEntity owner;

	private int page;

	private BulkStorageScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory display,
			Hand hand, int capacity) {
		super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, display, ROWS);
		this.display = display;
		this.hand = hand;
		this.capacity = capacity;
		this.owner = playerInventory.player;
		refresh();
	}

	public static void open(ServerPlayerEntity player, Hand hand, int capacity) {
		SimpleInventory display = new SimpleInventory(TOTAL_SLOTS);

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, ignored) -> new BulkStorageScreenHandler(syncId, inventory, display, hand, capacity),
				Text.translatable("container.slickfun.bulk_storage")));
	}

	// ------------------------------------------------------------------ the stored number

	private ItemStack container() {
		return this.owner.getStackInHand(this.hand);
	}

	private BulkStore store() {
		return BulkStorageItems.Bulk.storeOf(container());
	}

	private void setStore(BulkStore store) {
		ItemStack container = container();

		if (store.isEmpty()) {
			container.remove(ModComponents.BULK_STORE);
		} else {
			container.set(ModComponents.BULK_STORE, store);
		}
	}

	private int stackSize() {
		ItemStack sample = store().sample();
		return sample.isEmpty() ? 64 : sample.getMaxCount();
	}

	private int perPageItems() {
		return PER_PAGE * stackSize();
	}

	private int pageCount() {
		int total = store().count();
		return Math.max(1, (total + perPageItems() - 1) / perPageItems());
	}

	/** Adds what it can and returns how many were taken. */
	private int deposit(ItemStack incoming) {
		if (!acceptable(incoming)) {
			return 0;
		}

		BulkStore store = store();
		int moved = Math.min(incoming.getCount(), this.capacity - store.count());

		if (moved <= 0) {
			return 0;
		}

		setStore(store.with(incoming, moved, this.capacity));
		incoming.decrement(moved);
		return moved;
	}

	/**
	 * Removes up to {@code wanted} and hands them back as a stack.
	 *
	 * <p>Taking anything out switches auto-collect off. Without that the container grabs it
	 * straight back out of your inventory a fraction of a second later, and there is no way to
	 * get your things out at all.
	 */
	private ItemStack withdraw(int wanted) {
		BulkStore store = store();
		int moved = Math.min(wanted, store.count());

		if (moved <= 0) {
			return ItemStack.EMPTY;
		}

		ItemStack out = store.sample().copyWithCount(moved);
		setStore(store.less(moved));
		stopCollecting();
		return out;
	}

	private void stopCollecting() {
		ItemStack container = container();

		if (!BulkStorageItems.InsanelyLargeStorage.isCollecting(container)) {
			return;
		}

		BulkStorageItems.InsanelyLargeStorage.setCollecting(container, false);
		announce(false);
	}

	private void announce(boolean on) {
		this.owner.getWorld().playSound(null, this.owner.getX(), this.owner.getY(), this.owner.getZ(),
				SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.8F, on ? 1.4F : 0.6F);
		this.owner.sendMessage(Text.translatable(on
						? "message.slickfun.bulk.auto_on"
						: "message.slickfun.bulk.auto_off")
				.formatted(on ? Formatting.GREEN : Formatting.GOLD), false);
	}

	private boolean acceptable(ItemStack candidate) {
		if (candidate.isEmpty() || candidate.getItem() instanceof BulkStorageItems.Bulk) {
			return false;
		}

		BulkStore store = store();
		return store.sample().isEmpty() || ItemStack.areItemsAndComponentsEqual(store.sample(), candidate);
	}

	// ------------------------------------------------------------------ drawing it

	private void refresh() {
		BulkStore store = store();
		int total = store.count();
		int perPage = perPageItems();

		this.page = Math.max(0, Math.min(this.page, pageCount() - 1));

		int skipped = this.page * perPage;
		int onPage = Math.max(0, Math.min(total - skipped, perPage));
		ItemStack sample = store.sample();
		int left = onPage;

		for (int slot = 0; slot < PER_PAGE; slot++) {
			if (left <= 0 || sample.isEmpty()) {
				this.display.setStack(slot, ItemStack.EMPTY);
				continue;
			}

			int batch = Math.min(stackSize(), left);
			this.display.setStack(slot, sample.copyWithCount(batch));
			left -= batch;
		}

		for (int slot = NAV_ROW_START; slot < TOTAL_SLOTS; slot++) {
			this.display.setStack(slot, filler());
		}

		int pages = pageCount();

		if (this.page > 0) {
			this.display.setStack(PREV_SLOT, arrow("message.slickfun.menu.prev", this.page, pages));
		}

		if (this.page < pages - 1) {
			this.display.setStack(NEXT_SLOT, arrow("message.slickfun.menu.next", this.page + 2, pages));
		}

		// Middle of the navigation row reports the total, so the scale is visible at a glance.
		this.display.setStack(NAV_ROW_START + 4, counter(total));

		// The switch only exists once it has been fed pearls; before that there is nothing
		// to switch, and an inert button would only raise questions.
		if (BulkStorageItems.InsanelyLargeStorage.isAutomatic(container())) {
			this.display.setStack(TOGGLE_SLOT,
					toggle(BulkStorageItems.InsanelyLargeStorage.isCollecting(container())));
		}

		syncState();
	}

	// ------------------------------------------------------------------ clicks

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (slotIndex >= NAV_ROW_START && slotIndex < TOTAL_SLOTS) {
			navigate(slotIndex, player);
			return;
		}

		if (slotIndex >= 0 && slotIndex < PER_PAGE) {
			content(button, actionType, player);
			return;
		}

		// The player's own inventory, where shift clicking is the only special case.
		if (slotIndex >= TOTAL_SLOTS && actionType == SlotActionType.QUICK_MOVE) {
			ItemStack from = this.slots.get(slotIndex).getStack();

			if (deposit(from) > 0) {
				if (from.isEmpty()) {
					this.slots.get(slotIndex).setStack(ItemStack.EMPTY);
				}

				click(player, 1.2F);
			}

			refresh();
			return;
		}

		super.onSlotClick(slotIndex, button, actionType, player);
	}

	/**
	 * A click on the contents. Nothing here moves a stack between slots - it all becomes
	 * addition or subtraction on the total, and then the page is redrawn.
	 */
	private void content(int button, SlotActionType actionType, PlayerEntity player) {
		ItemStack cursor = getCursorStack();

		switch (actionType) {
			case PICKUP -> {
				if (!cursor.isEmpty()) {
					if (deposit(cursor) > 0) {
						setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
						click(player, 1.2F);
					}
				} else {
					// Left click takes a full stack, right click takes half.
					int wanted = button == 1 ? Math.max(1, stackSize() / 2) : stackSize();
					ItemStack taken = withdraw(wanted);

					if (!taken.isEmpty()) {
						setCursorStack(taken);
						click(player, 1.6F);
					}
				}
			}
			case QUICK_MOVE -> {
				ItemStack taken = withdraw(stackSize());

				if (!taken.isEmpty()) {
					int handed = taken.getCount();

					if (!player.getInventory().insertStack(taken)) {
						// Whatever would not fit goes straight back in rather than on the floor.
						deposit(taken);
						handed -= taken.getCount();
					}

					if (handed > 0) {
						click(player, 1.4F);
					}
				}
			}
			case THROW -> {
				ItemStack taken = withdraw(button == 1 ? stackSize() : 1);

				if (!taken.isEmpty()) {
					player.dropItem(taken, true);
				}
			}
			case SWAP -> {
				ItemStack hotbar = button == 40
						? player.getInventory().offHand.get(0)
						: player.getInventory().getStack(button);

				if (!hotbar.isEmpty()) {
					if (deposit(hotbar) > 0 && hotbar.isEmpty()) {
						if (button == 40) {
							player.getInventory().offHand.set(0, ItemStack.EMPTY);
						} else {
							player.getInventory().setStack(button, ItemStack.EMPTY);
						}
					}
				} else {
					ItemStack taken = withdraw(stackSize());

					if (!taken.isEmpty()) {
						if (button == 40) {
							player.getInventory().offHand.set(0, taken);
						} else {
							player.getInventory().setStack(button, taken);
						}
					}
				}
			}
			default -> {
				// Drags and anything else are refused rather than half-handled.
			}
		}

		refresh();
	}

	private void navigate(int slotIndex, PlayerEntity player) {
		if (slotIndex == PREV_SLOT && this.page > 0) {
			this.page--;
			click(player, 1.2F);
		} else if (slotIndex == NEXT_SLOT && this.page < pageCount() - 1) {
			this.page++;
			click(player, 1.5F);
		} else if (slotIndex == TOGGLE_SLOT && BulkStorageItems.InsanelyLargeStorage.isAutomatic(container())) {
			boolean turningOn = !BulkStorageItems.InsanelyLargeStorage.isCollecting(container());
			BulkStorageItems.InsanelyLargeStorage.setCollecting(container(), turningOn);
			announce(turningOn);
		}

		refresh();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		// All shift clicking is handled in onSlotClick, which never delegates here.
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		// Nothing is ever placed into the display by vanilla; deposits go through the total.
		return slot.inventory != this.display;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		this.display.clear();
		super.onClosed(player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getStackInHand(this.hand).getItem() instanceof BulkStorageItems.Bulk;
	}

	// ------------------------------------------------------------------ decoration

	private static ItemStack filler() {
		ItemStack pane = new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
		// A blank name stops the client drawing "White Stained Glass Pane" over everything.
		pane.set(DataComponentTypes.CUSTOM_NAME, Text.empty());
		return pane;
	}

	private ItemStack counter(int total) {
		ItemStack label = new ItemStack(Items.PAPER);
		label.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("message.slickfun.bulk.total",
				total, this.capacity).formatted(Formatting.AQUA));
		return label;
	}

	private static ItemStack toggle(boolean on) {
		ItemStack button = new ItemStack(on ? Items.LIME_DYE : Items.GRAY_DYE);
		button.set(DataComponentTypes.CUSTOM_NAME, Text.translatable(on
						? "message.slickfun.bulk.auto_button_on"
						: "message.slickfun.bulk.auto_button_off")
				.formatted(on ? Formatting.GREEN : Formatting.GRAY));
		return button;
	}

	private static ItemStack arrow(String key, int targetPage, int total) {
		ItemStack arrow = new ItemStack(Items.ARROW);
		arrow.set(DataComponentTypes.CUSTOM_NAME,
				Text.translatable(key, targetPage, total).formatted(Formatting.YELLOW));
		return arrow;
	}

	private static void click(PlayerEntity player, float pitch) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, pitch);
	}
}
