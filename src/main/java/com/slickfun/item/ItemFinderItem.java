package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModItems;
import com.slickfun.screen.SearchResultsScreenHandler;
import com.slickfun.util.ChestSearch;
import com.slickfun.util.SignPrompt;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Type what you are looking for, pick it off a list, and every container within 60 blocks
 * holding it lights up.
 *
 * <p>The plain finder points; the ender one also takes you there. Upgrading is done by
 * feeding it ender pearls rather than by a recipe, because a crafting slot consumes exactly
 * one item - "16 pearls per slot" is not something a shaped recipe can express.
 */
public class ItemFinderItem extends Item {
	public static final int PEARLS_TO_UPGRADE = 128;

	private final boolean canTeleport;

	public ItemFinderItem(Settings settings, boolean canTeleport) {
		super(settings);
		this.canTeleport = canTeleport;
	}

	public static int pearlsIn(ItemStack stack) {
		return stack.getOrDefault(ModComponents.PEARL_CHARGE, 0);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.fail(stack);
		}

		// Sneaking feeds it pearls instead of opening the search.
		if (!this.canTeleport && player.isSneaking()) {
			return feed(player, stack, hand);
		}

		boolean teleports = this.canTeleport;
		SignPrompt.ask(player, query -> {
			if (player.isRemoved()) {
				return;
			}

			if (query.isEmpty()) {
				player.sendMessage(Text.translatable("message.slickfun.finder.no_query").formatted(Formatting.GRAY), true);
				return;
			}

			SearchResultsScreenHandler.open(player, query, teleports);
		});

		return TypedActionResult.success(stack, false);
	}

	/** Swallows ender pearls from the inventory until it has enough to become the ender one. */
	private TypedActionResult<ItemStack> feed(ServerPlayerEntity player, ItemStack stack, Hand hand) {
		int held = pearlsIn(stack);
		int needed = PEARLS_TO_UPGRADE - held;
		int taken = 0;

		for (int slot = 0; slot < player.getInventory().size() && taken < needed; slot++) {
			ItemStack candidate = player.getInventory().getStack(slot);

			if (!candidate.isOf(Items.ENDER_PEARL)) {
				continue;
			}

			int take = Math.min(candidate.getCount(), needed - taken);
			candidate.decrement(take);
			taken += take;
		}

		if (taken == 0) {
			player.sendMessage(Text.translatable("message.slickfun.finder.need_pearls", needed).formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		int total = held + taken;
		ServerWorld world = player.getServerWorld();

		if (total >= PEARLS_TO_UPGRADE) {
			ItemStack upgraded = new ItemStack(ModItems.ENDER_ITEM_FINDER);
			player.setStackInHand(hand, upgraded);

			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.6F, 1.4F);
			world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getBodyY(1.0D), player.getZ(),
					80, 0.5D, 0.6D, 0.5D, 0.6D);
			player.sendMessage(Text.translatable("message.slickfun.finder.upgraded").formatted(Formatting.LIGHT_PURPLE), false);
		} else {
			stack.set(ModComponents.PEARL_CHARGE, total);
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5F, 1.8F);
			player.sendMessage(Text.translatable("message.slickfun.finder.absorbed", total, PEARLS_TO_UPGRADE)
					.formatted(Formatting.LIGHT_PURPLE), true);
		}

		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.item_finder.1", ChestSearch.RADIUS).formatted(Formatting.GRAY));

		if (this.canTeleport) {
			tooltip.add(Text.translatable("tooltip.slickfun.item_finder.teleports").formatted(Formatting.LIGHT_PURPLE));
		} else {
			tooltip.add(Text.translatable("tooltip.slickfun.item_finder.upgrade",
					pearlsIn(stack), PEARLS_TO_UPGRADE).formatted(Formatting.DARK_GRAY));
		}
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return this.canTeleport;
	}
}
