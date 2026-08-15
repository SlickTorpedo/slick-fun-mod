package com.slickfun.item;

import java.util.ArrayList;
import java.util.List;

import com.slickfun.util.LadderManager;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Click a wall and get a short ladder up it. The rungs are temporary and delete themselves,
 * so this is a way up rather than a way to duplicate ladders.
 */
public class InfiniteLaddersItem extends Item {
	private static final int COOLDOWN_TICKS = 20;

	public InfiniteLaddersItem(Settings settings) {
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

		Direction side = context.getSide();

		if (side.getAxis().isVertical()) {
			player.sendMessage(Text.translatable("message.slickfun.ladders.need_wall").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		BlockState ladder = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, side);
		List<BlockPos> placed = new ArrayList<>(LadderManager.MAX_RUNGS);
		BlockPos cursor = context.getBlockPos().offset(side);

		for (int rung = 0; rung < LadderManager.MAX_RUNGS; rung++) {
			if (!world.getBlockState(cursor).isReplaceable() || !ladder.canPlaceAt(world, cursor)) {
				break;
			}

			world.setBlockState(cursor, ladder, Block.NOTIFY_ALL);
			placed.add(cursor.toImmutable());
			cursor = cursor.up();
		}

		if (placed.isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.ladders.no_room").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		LadderManager.claim(player, placed);

		serverWorld.playSound(null, placed.get(0), SoundEvents.BLOCK_LADDER_PLACE, SoundCategory.BLOCKS, 0.8F, 1.0F);
		player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		player.sendMessage(Text.translatable("message.slickfun.ladders.placed", placed.size()).formatted(Formatting.GRAY), true);

		return ActionResult.SUCCESS;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.infinite_ladders.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.infinite_ladders.2", LadderManager.MAX_RUNGS).formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.infinite_ladders.3").formatted(Formatting.DARK_GRAY));
	}
}
