package com.slickfun.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slickfun.item.ExtinguisherItem;
import com.slickfun.item.WeaponItems;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Catches every left click, not just the ones that land on something.
 *
 * <p>Vanilla only tells the server about an attack when the client decides it hit an entity
 * inside melee reach. An extinguisher aimed at someone across the room produces no attack
 * packet at all - only a swing - so {@code AttackEntityCallback} never fires and nothing
 * happens. This is the one hook that sees the swing itself, whatever it did or did not hit.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class HandSwingMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onHandSwing", at = @At("TAIL"))
	private void slickfun$onSwing(HandSwingC2SPacket packet, CallbackInfo info) {
		ExtinguisherItem.onSwing(this.player, packet.getHand());
		WeaponItems.SwordOfLongArms.onSwing(this.player, packet.getHand());
	}
}
