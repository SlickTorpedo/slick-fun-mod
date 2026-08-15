package com.slickfun.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.slickfun.registry.ModItems;
import com.slickfun.screen.AdminMenuScreenHandler;
import com.slickfun.util.AdminUtil;
import com.slickfun.util.Recipes;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class SlickFunCommands {
	private SlickFunCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			register(dispatcher, "slickfun");
			register(dispatcher, "sfm");
		});
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
		dispatcher.register(CommandManager.literal(name)
				.then(CommandManager.literal("menu")
						.requires(source -> source.hasPermissionLevel(AdminUtil.REQUIRED_LEVEL))
						.executes(SlickFunCommands::openMenu))
				.then(CommandManager.literal("recipes")
						.executes(SlickFunCommands::grantRecipes))
				.then(CommandManager.literal("help")
						.executes(SlickFunCommands::help))
				.executes(SlickFunCommands::help));
	}

	private static int openMenu(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

		SimpleInventory display = AdminMenuScreenHandler.newDisplay();
		List<Item> catalogue = List.copyOf(ModItems.ALL);

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, playerInventory, ignored) -> new AdminMenuScreenHandler(syncId, playerInventory, display, catalogue),
				Text.translatable("container.slickfun.admin_menu")));

		player.sendMessage(Text.translatable("message.slickfun.menu.hint").formatted(Formatting.GRAY), false);
		return 1;
	}

	/** Puts every recipe this mod adds into the player's recipe book. */
	private static int grantRecipes(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		List<RecipeEntry<?>> recipes = Recipes.allFromThisMod(player);
		player.unlockRecipes(recipes);

		context.getSource().sendFeedback(
				() -> Text.translatable("message.slickfun.recipe.granted", recipes.size()).formatted(Formatting.GREEN),
				false);

		return recipes.size();
	}

	private static int help(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();

		source.sendFeedback(() -> Text.literal("Slick Fun Mod").formatted(Formatting.AQUA, Formatting.BOLD), false);
		line(source, "/slickfun recipes", "put every Slick Fun recipe in your recipe book");
		line(source, "/slickfun menu", "open the admin toy box (op level 2)");
		line(source, "Swiss Army Knife", "holds one of each portable tool");
		line(source, "Shulker Trader", "place a shulker box near an unemployed villager");

		return 1;
	}

	private static void line(ServerCommandSource source, String label, String description) {
		source.sendFeedback(() -> Text.literal("  " + label + " ").formatted(Formatting.YELLOW)
				.append(Text.literal("- " + description).formatted(Formatting.GRAY)), false);
	}
}
