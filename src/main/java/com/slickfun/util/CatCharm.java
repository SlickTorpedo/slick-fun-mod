package com.slickfun.util;

import com.slickfun.registry.ModItems;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * "Is this player wearing the Cat Collar?"
 *
 * <p>The trinket lookup this used to do by hand now lives in {@link TrinketCompat}, behind
 * {@link Carried}, so the collar answers the question the same way every other charm does.
 */
public final class CatCharm {
	private CatCharm() {
	}

	public static boolean isWornBy(LivingEntity entity) {
		return entity instanceof PlayerEntity player && Carried.has(player, ModItems.CAT_COLLAR);
	}
}
