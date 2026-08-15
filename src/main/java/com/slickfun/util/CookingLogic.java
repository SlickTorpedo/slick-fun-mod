package com.slickfun.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.slickfun.item.CookerType;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.world.ServerWorld;

/**
 * The smelting rules shared by the portable ovens and the Kiln block.
 *
 * <p>Nothing here defines its own recipe list. The kiln reads the ordinary vanilla smelting
 * recipes and simply declines anything a smoker or blast furnace already specialises in,
 * which means recipes added by other mods work in it without anyone doing anything.
 */
public final class CookingLogic {
	/** The three inventory slots every cooker uses, in vanilla furnace order. */
	public static final int INPUT_SLOT = 0;
	public static final int FUEL_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;

	/** {@link AbstractFurnaceBlockEntity#createFuelTimeMap()} rebuilds its map on every call. */
	private static Map<Item, Integer> fuelTimes;

	private CookingLogic() {
	}

	public static int fuelTimeOf(ItemStack stack) {
		if (fuelTimes == null) {
			fuelTimes = new HashMap<>(AbstractFurnaceBlockEntity.createFuelTimeMap());
		}

		return fuelTimes.getOrDefault(stack.getItem(), 0);
	}

	public static Optional<RecipeEntry<? extends AbstractCookingRecipe>> findRecipe(
			ServerWorld world, ItemStack input, CookerType type) {
		if (input.isEmpty()) {
			return Optional.empty();
		}

		SingleStackRecipeInput recipeInput = new SingleStackRecipeInput(input);

		if (type.materialsOnly()
				&& (world.getRecipeManager().getFirstMatch(RecipeType.SMOKING, recipeInput, world).isPresent()
				|| world.getRecipeManager().getFirstMatch(RecipeType.BLASTING, recipeInput, world).isPresent())) {
			return Optional.empty();
		}

		return world.getRecipeManager().getFirstMatch(type.recipeType(), recipeInput, world)
				.map(entry -> (RecipeEntry<? extends AbstractCookingRecipe>) entry);
	}

	public static ItemStack resultOf(ServerWorld world, RecipeEntry<? extends AbstractCookingRecipe> recipe, ItemStack input) {
		return recipe.value().craft(new SingleStackRecipeInput(input), world.getRegistryManager());
	}

	public static boolean outputHasRoom(ServerWorld world, RecipeEntry<? extends AbstractCookingRecipe> recipe, Inventory contents) {
		ItemStack result = resultOf(world, recipe, contents.getStack(INPUT_SLOT));

		if (result.isEmpty()) {
			return false;
		}

		ItemStack output = contents.getStack(OUTPUT_SLOT);

		if (output.isEmpty()) {
			return true;
		}

		return ItemStack.areItemsAndComponentsEqual(output, result)
				&& output.getCount() + result.getCount() <= output.getMaxCount();
	}

	/** Moves one craft's worth from input to output. Returns the experience it earned. */
	public static float craft(ServerWorld world, RecipeEntry<? extends AbstractCookingRecipe> recipe, Inventory contents) {
		ItemStack input = contents.getStack(INPUT_SLOT);
		ItemStack result = resultOf(world, recipe, input);
		ItemStack output = contents.getStack(OUTPUT_SLOT);

		if (output.isEmpty()) {
			contents.setStack(OUTPUT_SLOT, result.copy());
		} else {
			output.increment(result.getCount());
		}

		input.decrement(1);
		return recipe.value().getExperience();
	}

	/** Burns one item of fuel, leaving any bucket behind. Returns the burn time it bought. */
	public static int consumeFuel(Inventory contents) {
		ItemStack fuel = contents.getStack(FUEL_SLOT);
		int time = fuelTimeOf(fuel);

		if (time <= 0) {
			return 0;
		}

		Item remainder = fuel.getItem().getRecipeRemainder();
		fuel.decrement(1);

		if (fuel.isEmpty()) {
			contents.setStack(FUEL_SLOT, remainder == null ? ItemStack.EMPTY : new ItemStack(remainder));
		}

		return time;
	}
}
