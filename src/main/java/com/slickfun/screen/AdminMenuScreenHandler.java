package com.slickfun.screen;

import java.util.List;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.Recipes;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A read-only chest of every item in the mod, paged.
 *
 * <p>Left click hands out a copy; right click unlocks that item's recipe into your recipe
 * book. The bottom row is the navigation bar - arrows at each end, glass across the middle -
 * so the page control never eats a content slot.
 */
public class AdminMenuScreenHandler extends GenericContainerScreenHandler {
	private static final int ROWS = 6;
	private static final int TOTAL_SLOTS = ROWS * 9;
	private static final int NAV_ROW_START = TOTAL_SLOTS - 9;
	private static final int PER_PAGE = NAV_ROW_START;
	private static final int PREV_SLOT = NAV_ROW_START;
	private static final int NEXT_SLOT = TOTAL_SLOTS - 1;

	private final SimpleInventory display;
	private final List<Item> catalogue;
	private int page;

	public AdminMenuScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory display, List<Item> catalogue) {
		super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, display, ROWS);
		this.display = display;
		this.catalogue = catalogue;
		refresh();
	}

	public static SimpleInventory newDisplay() {
		return new SimpleInventory(TOTAL_SLOTS);
	}

	public int pageCount() {
		return Math.max(1, (this.catalogue.size() + PER_PAGE - 1) / PER_PAGE);
	}

	private void refresh() {
		int start = this.page * PER_PAGE;

		for (int slot = 0; slot < PER_PAGE; slot++) {
			int index = start + slot;
			this.display.setStack(slot, index < this.catalogue.size()
					? new ItemStack(this.catalogue.get(index))
					: ItemStack.EMPTY);
		}

		for (int slot = NAV_ROW_START; slot < TOTAL_SLOTS; slot++) {
			this.display.setStack(slot, filler());
		}

		if (this.page > 0) {
			this.display.setStack(PREV_SLOT, arrow("message.slickfun.menu.prev", this.page, pageCount()));
		}

		if (this.page < pageCount() - 1) {
			this.display.setStack(NEXT_SLOT, arrow("message.slickfun.menu.next", this.page + 2, pageCount()));
		}

		syncState();
	}

	private static ItemStack filler() {
		ItemStack pane = new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
		// A blank name stops the client drawing "White Stained Glass Pane" over everything.
		pane.set(DataComponentTypes.CUSTOM_NAME, Text.empty());
		return pane;
	}

	private static ItemStack arrow(String key, int targetPage, int total) {
		ItemStack arrow = new ItemStack(Items.ARROW);
		arrow.set(DataComponentTypes.CUSTOM_NAME,
				Text.translatable(key, targetPage, total).formatted(Formatting.YELLOW));
		return arrow;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (actionType == SlotActionType.QUICK_CRAFT) {
			resync();
			return;
		}

		if (slotIndex < 0 || slotIndex >= TOTAL_SLOTS) {
			super.onSlotClick(slotIndex, button, actionType, player);
			return;
		}

		if (slotIndex == PREV_SLOT && this.page > 0) {
			this.page--;
			refresh();
			click(player, 1.2F);
			return;
		}

		if (slotIndex == NEXT_SLOT && this.page < pageCount() - 1) {
			this.page++;
			refresh();
			click(player, 1.5F);
			return;
		}

		// The rest of the navigation row is decoration.
		if (slotIndex >= NAV_ROW_START) {
			resync();
			return;
		}

		handleMenuClick(slotIndex, button, actionType, player);
	}

	private void handleMenuClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		ItemStack displayed = this.display.getStack(slotIndex);

		if (displayed.isEmpty() || !AdminUtil.isAdmin(player)) {
			resync();
			return;
		}

		if (actionType == SlotActionType.PICKUP && button == 1) {
			showRecipe(displayed, player);
		} else {
			giveCopy(displayed, player);
		}

		resync();
	}

	private void giveCopy(ItemStack displayed, PlayerEntity player) {
		ItemStack gift = displayed.copy();

		if (!player.getInventory().insertStack(gift)) {
			player.dropItem(gift, false);
		}

		click(player, 1.6F);
	}

	private void showRecipe(ItemStack displayed, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}

		List<RecipeEntry<?>> recipes = Recipes.producing(serverPlayer, displayed.getItem());

		if (recipes.isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.recipe.none", displayed.getName())
					.formatted(Formatting.GRAY), true);
			return;
		}

		serverPlayer.unlockRecipes(recipes);
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.UI_TOAST_IN, SoundCategory.PLAYERS, 0.5F, 1.4F);
		player.sendMessage(Text.translatable("message.slickfun.recipe.unlocked", displayed.getName())
				.formatted(Formatting.GREEN), false);
	}

	private static void click(PlayerEntity player, float pitch) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, pitch);
	}

	/** The client optimistically moved a stack onto its cursor; put its view back. */
	private void resync() {
		this.setCursorStack(ItemStack.EMPTY);
		syncState();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.id >= TOTAL_SLOTS && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return AdminUtil.isAdmin(player);
	}
}
