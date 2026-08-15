package com.slickfun.registry;

import java.util.Optional;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;

/**
 * Trades for the Shulker Trader. Levelling one up makes plain boxes steadily cheaper and
 * unlocks the dyeing service, bulk shells and finally an ender chest.
 */
public final class ModTrades {
	private static final float PRICE_MULTIPLIER = 0.05F;

	/** Pool the "random colour" trades draw from. */
	private static final Item[] DYED_BOXES = new Item[] {
			Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.MAGENTA_SHULKER_BOX,
			Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX,
			Items.PINK_SHULKER_BOX, Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX,
			Items.CYAN_SHULKER_BOX, Items.PURPLE_SHULKER_BOX, Items.BLUE_SHULKER_BOX,
			Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.RED_SHULKER_BOX,
			Items.BLACK_SHULKER_BOX
	};

	private ModTrades() {
	}

	public static void register() {
		// Level 1 - Novice
		TradeOfferHelper.registerVillagerOffers(ModVillagers.shulkerTrader, 1, factories -> {
			factories.add(buy(Items.CHEST, 12, 1, 16, 2));
			factories.add(buy(Items.SHULKER_SHELL, 1, 5, 12, 2));
			factories.add(sell(Items.SHULKER_BOX, 1, 24, 4, 5));
		});

		// Level 2 - Apprentice
		TradeOfferHelper.registerVillagerOffers(ModVillagers.shulkerTrader, 2, factories -> {
			factories.add(buy(Items.ENDER_PEARL, 4, 1, 12, 10));
			factories.add(sell(Items.SHULKER_BOX, 1, 18, 6, 10));
			factories.add(new DyeServiceFactory(6, 8, 10));
		});

		// Level 3 - Journeyman
		TradeOfferHelper.registerVillagerOffers(ModVillagers.shulkerTrader, 3, factories -> {
			factories.add(buy(Items.SHULKER_SHELL, 1, 8, 12, 20));
			factories.add(new RandomBoxFactory(14, 8, 20));
			factories.add(sell(Items.SHULKER_SHELL, 1, 15, 6, 20));
		});

		// Level 4 - Expert
		TradeOfferHelper.registerVillagerOffers(ModVillagers.shulkerTrader, 4, factories -> {
			factories.add(sell(Items.SHULKER_BOX, 1, 12, 8, 30));
			factories.add(sell(Items.SHULKER_SHELL, 2, 26, 4, 30));
			factories.add(new RandomBoxFactory(11, 8, 30));
		});

		// Level 5 - Master
		TradeOfferHelper.registerVillagerOffers(ModVillagers.shulkerTrader, 5, factories -> {
			factories.add(sell(Items.SHULKER_BOX, 1, 8, 12, 40));
			factories.add(sell(Items.SHULKER_SHELL, 4, 30, 3, 40));
			factories.add(sell(Items.ENDER_CHEST, 1, 34, 3, 40));
			factories.add(new DyeServiceFactory(3, 12, 40));
		});
	}

	/** Villager pays emeralds for the player's items. */
	private static TradeOffers.Factory buy(Item wanted, int wantedCount, int emeralds, int maxUses, int xp) {
		return (entity, random) -> new TradeOffer(
				new TradedItem(wanted, wantedCount),
				new ItemStack(Items.EMERALD, emeralds),
				maxUses, xp, PRICE_MULTIPLIER);
	}

	/** Player pays emeralds for the villager's items. */
	private static TradeOffers.Factory sell(Item offered, int offeredCount, int emeralds, int maxUses, int xp) {
		return (entity, random) -> new TradeOffer(
				new TradedItem(Items.EMERALD, emeralds),
				new ItemStack(offered, offeredCount),
				maxUses, xp, PRICE_MULTIPLIER);
	}

	private static Item randomDyedBox(Random random) {
		return DYED_BOXES[random.nextInt(DYED_BOXES.length)];
	}

	/** Emeralds for a coloured box. The colour is rolled once, when the villager gets the trade. */
	private record RandomBoxFactory(int emeralds, int maxUses, int xp) implements TradeOffers.Factory {
		@Override
		public TradeOffer create(Entity entity, Random random) {
			return new TradeOffer(
					new TradedItem(Items.EMERALD, emeralds),
					new ItemStack(randomDyedBox(random), 1),
					maxUses, xp, PRICE_MULTIPLIER);
		}
	}

	/** Hand over a plain box plus some emeralds, get a coloured one back. */
	private record DyeServiceFactory(int emeralds, int maxUses, int xp) implements TradeOffers.Factory {
		@Override
		public TradeOffer create(Entity entity, Random random) {
			return new TradeOffer(
					new TradedItem(Items.EMERALD, emeralds),
					Optional.of(new TradedItem(Items.SHULKER_BOX, 1)),
					new ItemStack(randomDyedBox(random), 1),
					maxUses, xp, PRICE_MULTIPLIER);
		}
	}
}
