package com.slickfun.screen;

import com.slickfun.item.CookerType;
import com.slickfun.util.CookingLogic;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * The Kiln's screen. It borrows the blast furnace's handler type so the vanilla client draws
 * it with no client-side registration, but matches against ordinary smelting recipes with
 * the kiln's materials-only filter.
 */
public class KilnScreenHandler extends AbstractFurnaceScreenHandler {
	private final World world;

	public KilnScreenHandler(int syncId, PlayerInventory playerInventory) {
		super(ScreenHandlerType.BLAST_FURNACE, CookerType.KILN.recipeType(), RecipeBookCategory.BLAST_FURNACE,
				syncId, playerInventory);
		this.world = playerInventory.player.getWorld();
	}

	public KilnScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
		super(ScreenHandlerType.BLAST_FURNACE, CookerType.KILN.recipeType(), RecipeBookCategory.BLAST_FURNACE,
				syncId, playerInventory, inventory, properties);
		this.world = playerInventory.player.getWorld();
	}

	@Override
	protected boolean isSmeltable(ItemStack stack) {
		if (!(this.world instanceof ServerWorld serverWorld)) {
			return super.isSmeltable(stack);
		}

		return CookingLogic.findRecipe(serverWorld, stack, CookerType.KILN).isPresent();
	}
}
