package com.slickfun.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
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
 * Clears a whole connected mass of sand in one click, dropping all of it.
 *
 * <p>Digging sand by hand is miserable because it falls into the hole as you go. This takes
 * the entire connected body at once, which is the only way that actually helps.
 */
public class SandBlasterItem extends Item {
	/** How much it will take in one click. */
	public static final int MAX_BLOCKS = 100;

	private static final int COOLDOWN_TICKS = 20;

	public SandBlasterItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();

		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld level)) {
			return ActionResult.FAIL;
		}

		BlockPos clicked = context.getBlockPos();

		if (!isLoose(level.getBlockState(clicked))) {
			player.sendMessage(Text.translatable("message.slickfun.sandblaster.wrong_block").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		if (player.getItemCooldownManager().isCoolingDown(this)) {
			return ActionResult.FAIL;
		}

		List<BlockPos> body = gather(level, clicked);

		if (body.isEmpty()) {
			return ActionResult.FAIL;
		}

		player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

		// Highest first, so nothing left below turns into a falling block part way through and
		// starts a cascade while the rest is still being removed.
		body.sort(Comparator.comparingInt(BlockPos::getY).reversed());

		BlockState sample = level.getBlockState(clicked);

		for (BlockPos pos : body) {
			// breakBlock rather than a direct clear, so it drops exactly as if mined.
			level.breakBlock(pos, true, player);
		}

		level.playSound(null, clicked, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 1.4F, 0.7F);
		level.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, sample),
				clicked.getX() + 0.5D, clicked.getY() + 1.0D, clicked.getZ() + 0.5D,
				80, 1.2D, 0.8D, 1.2D, 0.15D);

		player.sendMessage(Text.translatable("message.slickfun.sandblaster.cleared", body.size())
				.formatted(Formatting.GRAY), true);

		return ActionResult.SUCCESS;
	}

	/**
	 * Flood fills the connected body, in all six directions.
	 *
	 * <p>Neighbours are only queued once, and the cap is checked as they are added rather than
	 * as they are removed, so a beach does not queue ten thousand positions before stopping.
	 */
	private static List<BlockPos> gather(ServerWorld level, BlockPos start) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		queue.add(start);
		seen.add(start);

		while (!queue.isEmpty() && found.size() < MAX_BLOCKS) {
			BlockPos current = queue.poll();

			if (!isLoose(level.getBlockState(current))) {
				continue;
			}

			found.add(current);

			for (Direction step : Direction.values()) {
				BlockPos neighbour = current.offset(step);

				if (found.size() + queue.size() >= MAX_BLOCKS) {
					break;
				}

				if (seen.add(neighbour) && isLoose(level.getBlockState(neighbour))) {
					queue.add(neighbour);
				}
			}
		}

		return found;
	}

	/**
	 * Sand, red sand and gravel.
	 *
	 * <p>Suspicious sand and gravel are deliberately excluded - they hold archaeology loot that
	 * is destroyed by anything other than a brush, and clearing a beach should not quietly
	 * throw away a dig site.
	 */
	public static boolean isLoose(BlockState state) {
		if (state.isOf(Blocks.SUSPICIOUS_SAND) || state.isOf(Blocks.SUSPICIOUS_GRAVEL)) {
			return false;
		}

		return state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.sand_blaster", MAX_BLOCKS).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.sand_blaster.2").formatted(Formatting.DARK_GRAY));
	}
}
