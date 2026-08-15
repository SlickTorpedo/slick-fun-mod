package com.slickfun.util;

import com.slickfun.item.WeaponItems;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

/**
 * Keeps the Sword of Long Arms off anything that is not a player.
 *
 * <p>The reach comes from an attribute modifier, which vanilla applies to everything the sword
 * can swing at - there is no way to say "further, but only for players" in the attribute
 * itself. So the restriction is enforced here instead, by refusing the hit.
 */
public final class WeaponManager {
	private WeaponManager() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient) {
				return ActionResult.PASS;
			}

			ItemStack held = player.getStackInHand(hand);

			if (!(held.getItem() instanceof WeaponItems.SwordOfLongArms) || entity instanceof PlayerEntity) {
				return ActionResult.PASS;
			}

			player.sendMessage(Text.translatable("message.slickfun.longarms.players_only").formatted(Formatting.GRAY), true);
			// Swallowed, so the swing lands on nothing rather than falling through to a normal hit.
			return ActionResult.FAIL;
		});
	}
}
