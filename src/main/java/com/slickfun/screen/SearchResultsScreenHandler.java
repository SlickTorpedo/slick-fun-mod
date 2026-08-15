package com.slickfun.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.slickfun.util.ChestSearch;
import com.slickfun.util.ServerScheduler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/** Everything matching the query. Click one and the search runs. */
public class SearchResultsScreenHandler extends GenericContainerScreenHandler {
	private static final int SLOTS = 54;

	private final SimpleInventory results;
	private final boolean canTeleport;

	private SearchResultsScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory results, boolean canTeleport) {
		super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, results, 6);
		this.results = results;
		this.canTeleport = canTeleport;
	}

	public static void open(ServerPlayerEntity player, String query, boolean canTeleport) {
		List<Item> matches = search(query);

		if (matches.isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.finder.no_items", query).formatted(Formatting.GRAY), false);
			return;
		}

		SimpleInventory inventory = new SimpleInventory(SLOTS);

		for (int slot = 0; slot < matches.size() && slot < SLOTS; slot++) {
			inventory.setStack(slot, new ItemStack(matches.get(slot)));
		}

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, playerInventory, ignored) -> new SearchResultsScreenHandler(syncId, playerInventory, inventory, canTeleport),
				Text.translatable("container.slickfun.finder_results", query)));
	}

	private static List<Item> search(String query) {
		String needle = query.toLowerCase(Locale.ROOT);
		List<Item> exact = new ArrayList<>();
		List<Item> partial = new ArrayList<>();

		for (Item item : Registries.ITEM) {
			if (item == net.minecraft.item.Items.AIR) {
				continue;
			}

			String name = new ItemStack(item).getName().getString().toLowerCase(Locale.ROOT);

			if (name.equals(needle)) {
				exact.add(item);
			} else if (name.contains(needle)) {
				partial.add(item);
			}
		}

		exact.addAll(partial);
		return exact;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (slotIndex >= 0 && slotIndex < SLOTS) {
			ItemStack chosen = this.results.getStack(slotIndex);

			if (!chosen.isEmpty() && player instanceof ServerPlayerEntity serverPlayer) {
				Item wanted = chosen.getItem();
				ServerScheduler.schedule(1, () -> runSearch(serverPlayer, wanted));
			}

			syncState();
			return;
		}

		// Nothing in the player's own inventory should move either.
		syncState();
	}

	private void runSearch(ServerPlayerEntity player, Item wanted) {
		if (player.isRemoved()) {
			return;
		}

		player.closeHandledScreen();

		ServerWorld world = player.getServerWorld();
		List<ChestSearch.Hit> hits = ChestSearch.find(world, player.getPos(), wanted);
		Text itemName = new ItemStack(wanted).getName();

		if (hits.isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.finder.nothing", itemName, ChestSearch.RADIUS)
					.formatted(Formatting.GRAY), false);
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.7F, 1.0F);
			return;
		}

		ChestSearch.highlight(world, player, hits);

		player.sendMessage(Text.translatable("message.slickfun.finder.found", hits.size(), itemName)
				.formatted(Formatting.AQUA, Formatting.BOLD), false);

		for (ChestSearch.Hit hit : hits) {
			player.sendMessage(Text.literal("  " + hit.count() + "x ").formatted(Formatting.YELLOW)
					.append(Text.translatable("message.slickfun.finder.at",
							hit.pos().getX(), hit.pos().getY(), hit.pos().getZ(), (int) hit.distance())
							.formatted(Formatting.GRAY)), false);
		}

		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.7F, 1.6F);

		if (this.canTeleport) {
			teleportTo(player, world, hits.get(0));
		}
	}

	private static void teleportTo(ServerPlayerEntity player, ServerWorld world, ChestSearch.Hit hit) {
		Vec3d spot = ChestSearch.landingSpot(world, hit.pos());

		if (spot == null) {
			player.sendMessage(Text.translatable("message.slickfun.finder.no_room").formatted(Formatting.GRAY), false);
			return;
		}

		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.requestTeleport(spot.x, spot.y, spot.z);
		player.fallDistance = 0.0F;
		world.playSound(null, spot.x, spot.y, spot.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.sendMessage(Text.translatable("message.slickfun.finder.teleported").formatted(Formatting.LIGHT_PURPLE), true);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return false;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
