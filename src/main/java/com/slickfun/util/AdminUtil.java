package com.slickfun.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class AdminUtil {
	/** Vanilla "gamemaster" level - the same level /gamemode and /summon require. */
	public static final int REQUIRED_LEVEL = 2;

	private AdminUtil() {
	}

	public static boolean isAdmin(PlayerEntity player) {
		return player.hasPermissionLevel(REQUIRED_LEVEL);
	}

	/**
	 * Returns true if the player may use an admin toy. Otherwise nags them once (server side
	 * only, so the message is not printed twice) and returns false.
	 */
	public static boolean checkAdmin(PlayerEntity player) {
		if (isAdmin(player)) {
			return true;
		}

		if (!player.getWorld().isClient) {
			player.sendMessage(Text.translatable("message.slickfun.no_permission").formatted(Formatting.RED), true);
		}

		return false;
	}
}
