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
				.then(CommandManager.literal("undo")
						.executes(SlickFunCommands::undo))
				.then(CommandManager.literal("update")
						.requires(source -> source.hasPermissionLevel(AdminUtil.REQUIRED_LEVEL))
						.executes(SlickFunCommands::update))
				.then(CommandManager.literal("help")
						.executes(SlickFunCommands::help))
				.executes(SlickFunCommands::help));
	}

	/** Takes back the last Builder Wand click. Driven by the click-to-undo line in chat. */
	private static int undo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		int removed = com.slickfun.util.BuildUndo.undo(player);

		if (removed < 0) {
			// The undo button stays in old chat lines forever, so hitting a spent one needs to
			// sound like nothing happened rather than looking like the click was missed.
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sound.SoundEvents.BLOCK_DISPENSER_FAIL,
					net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.wand.nothing_to_undo").formatted(Formatting.GRAY), true);
			return 0;
		}

		player.sendMessage(Text.translatable("message.slickfun.wand.undone",
				removed, com.slickfun.util.BuildUndo.depth(player)).formatted(Formatting.AQUA), true);
		return removed;
	}

	/** Checks the repository right now instead of waiting for the next poll. */
	private static int update(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();

		source.sendFeedback(() -> Text.translatable("message.slickfun.update.checking").formatted(Formatting.GRAY), false);

		// Logged as well as sent back: a console or RCON caller has usually gone by the time
		// the network round trip finishes, and its reply would go nowhere.
		com.slickfun.update.UpdateChecker.checkNow(result -> source.getServer().execute(() -> {
			com.slickfun.SlickFunMod.LOGGER.info("Update check: {}", result.getString());
			source.sendFeedback(() -> result, false);
		}));

		return 1;
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
