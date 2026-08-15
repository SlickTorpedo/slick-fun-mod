package com.slickfun.util;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Asks the player for a line of text using the sign editor.
 *
 * <p>The sign screen only opens for a real sign in the world - {@code openEditSignScreen}
 * sends the client the actual block at that position, and the reply handler looks the block
 * entity back up before accepting anything. So a sign genuinely has to exist, briefly.
 *
 * <p>It is placed only into air, never over anything, and with {@code NOTIFY_LISTENERS}
 * rather than {@code NOTIFY_ALL} so no neighbour updates fire - no redstone triggered, no
 * gravity blocks dislodged, no water flowing. It is removed the moment the player is done.
 *
 * <p>No mixin is needed to read the answer: vanilla's {@code tryChangeText} clears the sign's
 * editor UUID once it accepts an edit, so a null editor is the "they submitted" signal.
 */
public final class SignPrompt {
	private static final int TIMEOUT_TICKS = 20 * 60;

	private SignPrompt() {
	}

	public static void ask(ServerPlayerEntity player, Consumer<String> onSubmit) {
		ServerWorld world = player.getServerWorld();
		BlockPos spot = findSpot(world, player);

		if (spot == null) {
			onSubmit.accept("");
			return;
		}

		world.setBlockState(spot, Blocks.OAK_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);

		if (!(world.getBlockEntity(spot) instanceof SignBlockEntity sign)) {
			world.setBlockState(spot, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			onSubmit.accept("");
			return;
		}

		// Without this, tryChangeText refuses the edit and logs a warning.
		sign.setEditor(player.getUuid());
		player.openEditSignScreen(sign, true);

		poll(world, player, spot, onSubmit, 0);
	}

	private static void poll(ServerWorld world, ServerPlayerEntity player, BlockPos spot,
			Consumer<String> onSubmit, int elapsed) {
		ServerScheduler.schedule(1, () -> {
			if (!(world.getBlockEntity(spot) instanceof SignBlockEntity sign) || player.isRemoved()) {
				cleanUp(world, spot);
				return;
			}

			// Vanilla nulls the editor the instant it accepts the text.
			if (sign.getEditor() == null) {
				String typed = read(sign);
				cleanUp(world, spot);
				onSubmit.accept(typed);
				return;
			}

			if (elapsed >= TIMEOUT_TICKS) {
				cleanUp(world, spot);
				return;
			}

			poll(world, player, spot, onSubmit, elapsed + 1);
		});
	}

	private static String read(SignBlockEntity sign) {
		SignText text = sign.getFrontText();
		StringBuilder typed = new StringBuilder();

		for (int line = 0; line < 4; line++) {
			String content = text.getMessage(line, false).getString().trim();

			if (!content.isEmpty()) {
				if (!typed.isEmpty()) {
					typed.append(' ');
				}

				typed.append(content);
			}
		}

		return typed.toString().trim();
	}

	private static void cleanUp(ServerWorld world, BlockPos spot) {
		if (world.getBlockState(spot).isOf(Blocks.OAK_SIGN)) {
			world.setBlockState(spot, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** Somewhere out of the way and empty. Never replaces an existing block. */
	private static BlockPos findSpot(ServerWorld world, ServerPlayerEntity player) {
		BlockPos base = player.getBlockPos();
		BlockPos[] candidates = {
				base.up(3), base.up(4), base.up(2),
				base.north(2).up(2), base.south(2).up(2), base.east(2).up(2), base.west(2).up(2)
		};

		for (BlockPos candidate : candidates) {
			if (world.isInBuildLimit(candidate) && world.getBlockState(candidate).isAir()) {
				return candidate;
			}
		}

		return null;
	}
}
