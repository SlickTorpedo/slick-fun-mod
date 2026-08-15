package com.slickfun.item;

import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.screen.ScreenHandlerType;

/**
 * The three flavours of pocket oven.
 *
 * <p>The Kiln is the new one: it runs ordinary smelting recipes at double speed, but refuses
 * anything a smoker or a blast furnace already specialises in. That leaves it as the fast
 * option for building materials - sand to glass, clay to brick, cobblestone to stone, logs
 * to charcoal - without stepping on the other two. Because it reads the vanilla smelting
 * recipe list rather than a list of its own, recipes added by other mods work in it for free.
 */
public enum CookerType {
	FURNACE(RecipeType.SMELTING, RecipeBookCategory.FURNACE, 1.0F, false),
	SMOKER(RecipeType.SMOKING, RecipeBookCategory.SMOKER, 1.0F, false),
	KILN(RecipeType.SMELTING, RecipeBookCategory.BLAST_FURNACE, 2.0F, true);

	private final RecipeType<? extends AbstractCookingRecipe> recipeType;
	private final RecipeBookCategory category;
	private final float speed;
	private final boolean materialsOnly;

	CookerType(RecipeType<? extends AbstractCookingRecipe> recipeType, RecipeBookCategory category,
			float speed, boolean materialsOnly) {
		this.recipeType = recipeType;
		this.category = category;
		this.speed = speed;
		this.materialsOnly = materialsOnly;
	}

	public RecipeType<? extends AbstractCookingRecipe> recipeType() {
		return recipeType;
	}

	public RecipeBookCategory category() {
		return category;
	}

	/** How much faster than the recipe's stated cooking time this cooker runs. */
	public float speed() {
		return speed;
	}

	/** True if food and ores should be turned away - see the class note. */
	public boolean materialsOnly() {
		return materialsOnly;
	}

	/** The vanilla screen this borrows, so no client-side screen registration is needed. */
	public ScreenHandlerType<?> screenType() {
		return switch (this) {
			case FURNACE -> ScreenHandlerType.FURNACE;
			case SMOKER -> ScreenHandlerType.SMOKER;
			case KILN -> ScreenHandlerType.BLAST_FURNACE;
		};
	}

	public String translationKey() {
		return switch (this) {
			case FURNACE -> "container.furnace";
			case SMOKER -> "container.smoker";
			case KILN -> "container.slickfun.kiln";
		};
	}
}
