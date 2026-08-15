package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.util.SoulBond;
import com.slickfun.util.SoulTieManager;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A two-way bond between players.
 *
 * <p>Tying requires both people to click the other, so nobody is ever bound without agreeing.
 * Right click to be pulled to them; crouch and right click to feed the tie the materials that
 * let it reach the Nether and the End.
 */
public class SoulTieItem extends Item {
	private static final int TELEPORT_COOLDOWN_TICKS = 20 * 10;

	public SoulTieItem(Settings settings) {
		super(settings);
	}

	public static SoulBond bondOf(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SOUL_BOND, SoulBond.EMPTY);
	}

	// ------------------------------------------------------------------ tying

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (user.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(user instanceof ServerPlayerEntity player) || !(entity instanceof ServerPlayerEntity target)) {
			return ActionResult.PASS;
		}

		SoulTieManager.offer(player, target, stack);
		return ActionResult.CONSUME;
	}

	// ------------------------------------------------------------------ feeding and travel

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.fail(stack);
		}

		if (player.isSneaking()) {
			return feed(player, stack);
		}

		if (player.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.fail(stack);
		}

		if (SoulTieManager.travel(player, stack)) {
			player.getItemCooldownManager().set(this, TELEPORT_COOLDOWN_TICKS);
			return TypedActionResult.success(stack);
		}

		return TypedActionResult.fail(stack);
	}

	/** Pours in whatever you are carrying, up to what each half of the tie still needs. */
	private static TypedActionResult<ItemStack> feed(ServerPlayerEntity player, ItemStack stack) {
		SoulBond bond = bondOf(stack);
		int pearlsWanted = SoulBond.PEARL_COST - bond.pearls();
		int rackWanted = SoulBond.NETHERRACK_COST - bond.netherrack();

		if (pearlsWanted <= 0 && rackWanted <= 0) {
			player.sendMessage(Text.translatable("message.slickfun.soul.full").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		int pearls = take(player, Items.ENDER_PEARL, pearlsWanted);
		int netherrack = take(player, Items.NETHERRACK, rackWanted);

		if (pearls == 0 && netherrack == 0) {
			player.sendMessage(Text.translatable("message.slickfun.soul.nothing_to_feed",
					pearlsWanted, rackWanted).formatted(Formatting.GRAY), false);
			return TypedActionResult.fail(stack);
		}

		SoulBond fedBond = bond.fed(pearls, netherrack);
		stack.set(ModComponents.SOUL_BOND, fedBond);

		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 0.6F);
		player.sendMessage(Text.translatable("message.slickfun.soul.fed",
				fedBond.pearls(), SoulBond.PEARL_COST,
				fedBond.netherrack(), SoulBond.NETHERRACK_COST).formatted(Formatting.LIGHT_PURPLE), false);

		return TypedActionResult.success(stack);
	}

	private static int take(ServerPlayerEntity player, Item wanted, int limit) {
		int taken = 0;

		for (int slot = 0; slot < player.getInventory().size() && taken < limit; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (!stack.isOf(wanted)) {
				continue;
			}

			int moved = Math.min(limit - taken, stack.getCount());
			stack.decrement(moved);
			taken += moved;

			if (stack.isEmpty()) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
			}
		}

		return taken;
	}

	// ------------------------------------------------------------------ tooltip

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		SoulBond bond = bondOf(stack);

		if (bond.isBound()) {
			tooltip.add(Text.translatable("tooltip.slickfun.soul_tie.bound", bond.partnerName())
					.formatted(Formatting.LIGHT_PURPLE));
		} else {
			tooltip.add(Text.translatable("tooltip.slickfun.soul_tie.unbound").formatted(Formatting.GRAY));
		}

		tooltip.add(charge("tooltip.slickfun.soul_tie.end", bond.pearls(), SoulBond.PEARL_COST));
		tooltip.add(charge("tooltip.slickfun.soul_tie.nether", bond.netherrack(), SoulBond.NETHERRACK_COST));
		tooltip.add(Text.translatable("tooltip.slickfun.soul_tie.sever").formatted(Formatting.DARK_RED, Formatting.ITALIC));
	}

	private static Text charge(String key, int have, int need) {
		return Text.translatable(key, have, need)
				.formatted(have >= need ? Formatting.GREEN : Formatting.DARK_GRAY);
	}

	/** What is left when a tie is cut. Inert, and only kept for what it says on the tooltip. */
	public static class Broken extends Item {
		public Broken(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			SoulBond bond = stack.getOrDefault(ModComponents.SOUL_BOND, SoulBond.EMPTY);

			if (!bond.partnerName().isEmpty()) {
				tooltip.add(Text.translatable("tooltip.slickfun.broken_soul_tie.memory", bond.partnerName())
						.formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
			}

			tooltip.add(Text.translatable("tooltip.slickfun.broken_soul_tie").formatted(Formatting.DARK_GRAY));
		}
	}
}
