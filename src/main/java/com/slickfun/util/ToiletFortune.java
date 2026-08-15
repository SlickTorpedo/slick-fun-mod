package com.slickfun.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.slickfun.registry.ModItems;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * What you might find down there.
 *
 * <p>Rolling is gated on having actually gone somewhere since the last roll. That is quiet
 * on purpose - nothing tells the player - it just means parking on one block and spamming
 * the toilet never pays out. The cooldown handles the pace; this handles the standing still.
 */
public final class ToiletFortune {
	/** How far you must have travelled since the last roll for the next one to count. */
	private static final double REQUIRED_TRAVEL = 8.0D;

	private static final double DIAMOND_CHANCE = 0.0001D;   // 0.01%
	private static final double AMAZING_CHANCE = 0.0006D;   // a further 0.05%
	private static final double GOOD_CHANCE = 0.0016D;      // a further 0.1%

	private static final Map<UUID, Vec3d> LAST_ROLL = new HashMap<>();

	private ToiletFortune() {
	}

	/** Good: a genuinely useful windfall. */
	private static final List<Supplier<ItemStack>> GOOD = List.of(
			() -> new ItemStack(Items.GOLD_BLOCK, 32),
			() -> new ItemStack(Items.DIAMOND_BLOCK, 10),
			() -> new ItemStack(Items.ELYTRA),
			() -> new ItemStack(Items.EMERALD_BLOCK, 16),
			() -> new ItemStack(Items.ANCIENT_DEBRIS, 8)
	);

	/** Amazing: the sort of thing you tell people about. */
	private static final List<Supplier<ItemStack>> AMAZING = List.of(
			() -> new ItemStack(Items.NETHERITE_BLOCK, 2),
			() -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 16),
			() -> new ItemStack(Items.BEACON, 2),
			() -> new ItemStack(Items.TOTEM_OF_UNDYING, 8),
			() -> new ItemStack(Items.NETHER_STAR, 3)
	);

	public enum Tier {
		NOTHING,
		GOOD,
		AMAZING,
		DIAMOND
	}

	public record Result(Tier tier, ItemStack prize) {
		public static final Result NOTHING = new Result(Tier.NOTHING, ItemStack.EMPTY);
	}

	/** True if the player has moved far enough since their last roll to earn another. */
	public static boolean hasWandered(ServerPlayerEntity player) {
		Vec3d last = LAST_ROLL.get(player.getUuid());
		return last == null || last.squaredDistanceTo(player.getPos()) >= REQUIRED_TRAVEL * REQUIRED_TRAVEL;
	}

	public static void markRolled(ServerPlayerEntity player) {
		LAST_ROLL.put(player.getUuid(), player.getPos());
	}

	public static void forget(UUID playerId) {
		LAST_ROLL.remove(playerId);
	}

	public static Result roll(Random random) {
		double value = random.nextDouble();

		if (value < DIAMOND_CHANCE) {
			return new Result(Tier.DIAMOND, new ItemStack(ModItems.DIAMOND_POOP));
		}

		if (value < AMAZING_CHANCE) {
			return new Result(Tier.AMAZING, AMAZING.get(random.nextInt(AMAZING.size())).get());
		}

		if (value < GOOD_CHANCE) {
			return new Result(Tier.GOOD, GOOD.get(random.nextInt(GOOD.size())).get());
		}

		return Result.NOTHING;
	}
}
