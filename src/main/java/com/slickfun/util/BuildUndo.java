package com.slickfun.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A stack of recent wand placements, so a misfire can be taken back.
 *
 * <p>Kept per player and several deep, because the mistake you want to undo is often not the
 * last thing you did - filling a wall the wrong way usually takes two or three clicks before
 * you notice.
 *
 * <p>Undoing only removes blocks that are still exactly what the wand put there. If someone
 * has since built over the spot, that block is theirs and is left alone.
 */
public final class BuildUndo {
	/** How many clicks back you can go. */
	public static final int DEPTH = 10;

	private record Placement(RegistryKey<World> dimension, List<BlockPos> positions, BlockState state, Item material) {
	}

	private static final Map<UUID, Deque<Placement>> HISTORY = new HashMap<>();

	private BuildUndo() {
	}

	public static void record(ServerPlayerEntity player, List<BlockPos> positions, BlockState state, Item material) {
		if (positions.isEmpty()) {
			return;
		}

		Deque<Placement> stack = HISTORY.computeIfAbsent(player.getUuid(), key -> new ArrayDeque<>());
		stack.push(new Placement(player.getServerWorld().getRegistryKey(), List.copyOf(positions), state, material));

		while (stack.size() > DEPTH) {
			stack.removeLast();
		}
	}

	public static int depth(ServerPlayerEntity player) {
		Deque<Placement> stack = HISTORY.get(player.getUuid());
		return stack == null ? 0 : stack.size();
	}

	/** @return how many blocks were taken back, or -1 if there was nothing to undo. */
	public static int undo(ServerPlayerEntity player) {
		Deque<Placement> stack = HISTORY.get(player.getUuid());

		if (stack == null || stack.isEmpty()) {
			return -1;
		}

		Placement placement = stack.pop();
		ServerWorld world = player.getServer().getWorld(placement.dimension());

		if (world == null) {
			return -1;
		}

		List<BlockPos> removed = new ArrayList<>();

		for (BlockPos pos : placement.positions()) {
			// Only ours. Anything changed since is somebody's work.
			if (world.getBlockState(pos).equals(placement.state())) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				removed.add(pos);
			}
		}

		if (!removed.isEmpty() && !player.isCreative()) {
			BlockSupply.refund(player, placement.material(), removed.size());
		}

		BlockPos first = removed.isEmpty() ? player.getBlockPos() : removed.get(0);
		world.playSound(null, first, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8F, 0.7F);

		return removed.size();
	}

	/**
	 * The click-to-undo line.
	 *
	 * <p>A run_command click is the only way to get a button into chat without a client mod,
	 * and it is why {@code /slickfun undo} exists as a command at all rather than being
	 * something the wand handles by itself.
	 */
	public static Text prompt(int placed, int remaining) {
		Text button = Text.translatable("message.slickfun.wand.undo_button")
				.styled(style -> style
						.withColor(Formatting.AQUA)
						.withUnderline(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/slickfun undo"))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Text.translatable("message.slickfun.wand.undo_hover", remaining))));

		return Text.translatable("message.slickfun.wand.placed", placed)
				.formatted(Formatting.GRAY)
				.append(Text.literal(" "))
				.append(button);
	}
}
