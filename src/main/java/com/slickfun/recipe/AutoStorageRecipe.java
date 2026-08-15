package com.slickfun.recipe;

import com.slickfun.item.BulkStorageItems;
import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModRecipes;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * Surrounding an Insanely Large Storage with ender pearls makes it collect on its own.
 *
 * <p>This is a coded recipe rather than a JSON one for a single reason: an ordinary shaped
 * recipe builds its result from scratch, which would throw away the container's contents. A
 * player upgrading a full one would lose sixty-nine thousand items. Here the input stack is
 * copied, so everything inside comes through untouched.
 *
 * <p>It also refuses a container that is already automatic, so the pearls cannot be wasted on
 * one that would gain nothing.
 */
public class AutoStorageRecipe extends SpecialCraftingRecipe {
	private static final int CENTRE = 4;

	public AutoStorageRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		if (input.getWidth() != 3 || input.getHeight() != 3) {
			return false;
		}

		ItemStack middle = input.getStackInSlot(CENTRE);

		if (!(middle.getItem() instanceof BulkStorageItems.InsanelyLargeStorage)
				|| BulkStorageItems.InsanelyLargeStorage.isAutomatic(middle)) {
			return false;
		}

		for (int slot = 0; slot < input.getSize(); slot++) {
			if (slot != CENTRE && !input.getStackInSlot(slot).isOf(Items.ENDER_PEARL)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
		// A copy of the original, so the bulk contents survive the upgrade.
		ItemStack upgraded = input.getStackInSlot(CENTRE).copyWithCount(1);
		upgraded.set(ModComponents.AUTO_STORE, true);
		return upgraded;
	}

	@Override
	public boolean fits(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.AUTO_STORAGE;
	}
}
