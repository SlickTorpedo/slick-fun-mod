package com.slickfun.util;

import com.slickfun.block.OwnedBlockEntity;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Stops other players breaking your Mail Box.
 *
 * <p>Without this the ownership check inside the box is decoration: anyone who wants what is
 * inside can simply mine the block and pick the contents up off the floor.
 *
 * <p>Operators are exempt, so nothing here can leave a server with an unremovable block.
 */
public final class BlockOwnership {
	private BlockOwnership() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world.isClient || !(blockEntity instanceof OwnedBlockEntity owned)) {
				return true;
			}

			if (mayBreak(player, owned)) {
				return true;
			}

			player.sendMessage(Text.translatable("message.slickfun.owned.protected", owned.ownerName())
					.formatted(Formatting.RED), true);
			return false;
		});
	}

	private static boolean mayBreak(PlayerEntity player, OwnedBlockEntity owned) {
		return player.isCreative() || player.hasPermissionLevel(2) || owned.isOwner(player);
	}
}
