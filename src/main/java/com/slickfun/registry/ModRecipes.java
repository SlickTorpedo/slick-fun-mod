package com.slickfun.registry;

import com.slickfun.SlickFunMod;
import com.slickfun.recipe.AutoStorageRecipe;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Recipes that need code behind them rather than a fixed shape in JSON. */
public final class ModRecipes {
	public static final RecipeSerializer<AutoStorageRecipe> AUTO_STORAGE = Registry.register(
			Registries.RECIPE_SERIALIZER, SlickFunMod.id("auto_storage"),
			new SpecialRecipeSerializer<>(AutoStorageRecipe::new));

	private ModRecipes() {
	}

	public static void register() {
		SlickFunMod.LOGGER.info("Registered recipe serializers.");
	}
}
