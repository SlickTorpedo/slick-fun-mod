package com.slickfun.util;

import com.slickfun.item.WeaponItems;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

/**
 * Keeps the Sword of Long Arms off anything that is not a fair target.
 *
 * <p>The reach comes from an attribute modifier, which vanilla applies to everything the sword
 * can swing at - there is no way to say "further, but only for these" in the attribute itself.
 * So the restriction is enforced here instead, by refusing the hit on anything that is neither
 * a player nor hostile. Ten blocks of reach that killed livestock would clear a farm by
 * accident on the walk past it.
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

			if (!(held.getItem() instanceof WeaponItems.SwordOfLongArms)) {
				return ActionResult.PASS;
			}

			// Players and anything hostile are fair game; farm animals are not.
			if (entity instanceof LivingEntity living && WeaponItems.isSwordTarget(living)) {
				return ActionResult.PASS;
			}

			player.sendMessage(Text.translatable("message.slickfun.longarms.players_only").formatted(Formatting.GRAY), true);
			// Swallowed, so the swing lands on nothing rather than falling through to a normal hit.
			return ActionResult.FAIL;
		});
	}
}
