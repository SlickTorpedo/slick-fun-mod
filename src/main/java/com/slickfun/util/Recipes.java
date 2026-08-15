package com.slickfun.util;

import java.util.List;

import com.slickfun.SlickFunMod;

import net.minecraft.item.Item;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Recipe lookups for the "show me how to make this" paths. Unlocking a recipe puts it in the
 * player's own recipe book, which is a better viewer than anything worth reimplementing.
 */
public final class Recipes {
	private Recipes() {
	}

	/** Every loaded recipe that produces {@code item}. */
	public static List<RecipeEntry<?>> producing(ServerPlayerEntity player, Item item) {
		MinecraftServer server = player.getServer();

		if (server == null) {
			return List.of();
		}

		return server.getRecipeManager().values().stream()
				.filter(entry -> entry.value().getResult(player.getRegistryManager()).isOf(item))
				.toList();
	}

	/** Every recipe this mod adds. */
	public static List<RecipeEntry<?>> allFromThisMod(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();

		if (server == null) {
			return List.of();
		}

		return server.getRecipeManager().values().stream()
				.filter(entry -> entry.id().getNamespace().equals(SlickFunMod.MOD_ID))
				.toList();
	}
}
