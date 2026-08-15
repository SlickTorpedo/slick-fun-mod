package com.slickfun.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Wands that move a lot of blocks in one click. */
public final class BuildingTools {
	private static final int MAX_BLOCKS = 8;

	private BuildingTools() {
	}

	/**
	 * Extends the surface you clicked outwards along its own plane.
	 *
	 * <p>Walks the contiguous run of identical blocks touching the clicked one, staying on
	 * the face you clicked, and places a copy in front of each. Only ever adds blocks, and
	 * only where there is already empty space, so it cannot damage anything you have built.
	 */
	public static class BuilderWand extends Item {
		public BuilderWand(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return ActionResult.FAIL;
			}

			BlockPos clicked = context.getBlockPos();
			BlockState template = world.getBlockState(clicked);
			Direction face = context.getSide();

			if (template.isAir() || template.getBlock() == Blocks.BEDROCK) {
				return ActionResult.FAIL;
			}

			Item needed = template.getBlock().asItem();
			int available = player.isCreative() ? MAX_BLOCKS : countIn(player, needed);

			if (available <= 0) {
				player.sendMessage(Text.translatable("message.slickfun.wand.no_blocks", template.getBlock().getName())
						.formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			List<BlockPos> targets = plan(world, clicked, template, face, Math.min(available, MAX_BLOCKS));

			if (targets.isEmpty()) {
				player.sendMessage(Text.translatable("message.slickfun.wand.no_room").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			for (BlockPos target : targets) {
				serverWorld.setBlockState(target, template, Block.NOTIFY_ALL);
			}

			if (!player.isCreative()) {
				consume(player, needed, targets.size());
			}

			serverWorld.playSound(null, clicked, template.getSoundGroup().getPlaceSound(),
					SoundCategory.BLOCKS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.wand.placed", targets.size())
					.formatted(Formatting.GRAY), true);

			return ActionResult.SUCCESS;
		}

		/** Flood fill across the clicked face, collecting spots that are free in front. */
		private static List<BlockPos> plan(World world, BlockPos start, BlockState template, Direction face, int limit) {
			List<BlockPos> targets = new ArrayList<>();
			Set<BlockPos> seen = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();

			queue.add(start);
			seen.add(start);

			while (!queue.isEmpty() && targets.size() < limit) {
				BlockPos current = queue.poll();

				if (!world.getBlockState(current).equals(template)) {
					continue;
				}

				BlockPos front = current.offset(face);

				if (world.getBlockState(front).isReplaceable()) {
					targets.add(front);
				}

				// Spread only within the plane of the clicked face.
				for (Direction step : Direction.values()) {
					if (step.getAxis() == face.getAxis()) {
						continue;
					}

					BlockPos neighbour = current.offset(step);

					if (seen.add(neighbour) && world.getBlockState(neighbour).equals(template)) {
						queue.add(neighbour);
					}
				}
			}

			return targets;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.builder_wand.1", MAX_BLOCKS).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.builder_wand.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Swaps a run of one block for whatever is in your off hand. */
	public static class ExchangeWand extends Item {
		private static final int MAX_SWAPS = 32;

		public ExchangeWand(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return ActionResult.FAIL;
			}

			ItemStack replacement = player.getOffHandStack();

			if (!(replacement.getItem() instanceof BlockItem blockItem)) {
				player.sendMessage(Text.translatable("message.slickfun.exchange.need_block").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			BlockState target = world.getBlockState(context.getBlockPos());
			BlockState newState = blockItem.getBlock().getDefaultState();

			if (target.isAir() || target.equals(newState) || target.getBlock() == Blocks.BEDROCK) {
				return ActionResult.FAIL;
			}

			int budget = player.isCreative() ? MAX_SWAPS : Math.min(MAX_SWAPS, replacement.getCount());
			List<BlockPos> swapped = new ArrayList<>();
			Set<BlockPos> seen = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();

			queue.add(context.getBlockPos());
			seen.add(context.getBlockPos());

			while (!queue.isEmpty() && swapped.size() < budget) {
				BlockPos current = queue.poll();

				if (!world.getBlockState(current).equals(target)) {
					continue;
				}

				swapped.add(current);

				for (Direction step : Direction.values()) {
					BlockPos neighbour = current.offset(step);

					if (seen.add(neighbour) && world.getBlockState(neighbour).equals(target)) {
						queue.add(neighbour);
					}
				}
			}

			for (BlockPos pos : swapped) {
				Block.dropStacks(target, serverWorld, pos);
				serverWorld.setBlockState(pos, newState, Block.NOTIFY_ALL);
			}

			if (!player.isCreative()) {
				replacement.decrement(swapped.size());
			}

			player.sendMessage(Text.translatable("message.slickfun.exchange.done", swapped.size())
					.formatted(Formatting.GRAY), true);
			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.exchange_wand.1", MAX_SWAPS).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.exchange_wand.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/**
	 * Recolours a dyed block in place.
	 *
	 * <p>Works off block ids rather than a hand-written table: {@code red_wool} is
	 * {@code <colour>_wool}, so swapping the prefix covers wool, concrete, terracotta, glass,
	 * carpet, candles, beds, shulker boxes and anything else following the same convention -
	 * including blocks from other mods.
	 */
	public static class PaintRoller extends Item {
		private static final String[] COLOURS = {
				"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
				"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
		};

		public PaintRoller(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
				return ActionResult.FAIL;
			}

			String wanted = dyeColourOf(player.getOffHandStack());

			if (wanted == null) {
				player.sendMessage(Text.translatable("message.slickfun.roller.need_dye").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			BlockPos pos = context.getBlockPos();
			BlockState current = world.getBlockState(pos);
			Identifier id = Registries.BLOCK.getId(current.getBlock());
			String path = id.getPath();

			for (String colour : COLOURS) {
				if (!path.startsWith(colour + "_")) {
					continue;
				}

				String family = path.substring(colour.length() + 1);

				if (colour.equals(wanted)) {
					return ActionResult.FAIL;
				}

				Identifier repainted = Identifier.of(id.getNamespace(), wanted + "_" + family);
				Block block = Registries.BLOCK.get(repainted);

				if (block == Blocks.AIR) {
					break;
				}

				// Carry over facing, half, and anything else the two share.
				BlockState newState = block.getStateWithProperties(current);
				world.setBlockState(pos, newState, Block.NOTIFY_ALL);
				world.playSound(null, pos, newState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.8F, 1.2F);

				if (!player.isCreative()) {
					player.getOffHandStack().decrement(1);
				}

				return ActionResult.SUCCESS;
			}

			player.sendMessage(Text.translatable("message.slickfun.roller.not_dyeable").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		private static String dyeColourOf(ItemStack stack) {
			if (stack.isEmpty()) {
				return null;
			}

			String path = Registries.ITEM.getId(stack.getItem()).getPath();

			if (!path.endsWith("_dye")) {
				return null;
			}

			return path.substring(0, path.length() - 4).toLowerCase(Locale.ROOT);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.paint_roller.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.paint_roller.2").formatted(Formatting.DARK_GRAY));
		}
	}

	// ---------------------------------------------------------------- shared helpers

	static int countIn(PlayerEntity player, Item item) {
		int total = 0;

		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (stack.isOf(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	static void consume(PlayerEntity player, Item item, int amount) {
		for (int slot = 0; slot < player.getInventory().size() && amount > 0; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (!stack.isOf(item)) {
				continue;
			}

			int take = Math.min(stack.getCount(), amount);
			stack.decrement(take);
			amount -= take;
		}
	}
}
