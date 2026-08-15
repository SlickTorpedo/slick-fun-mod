package com.slickfun.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slickfun.registry.ModItems;

import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A one-in-ten chance of a Cat Collar when a cat accepts you.
 *
 * <p>{@code tryTame} is only reached while the cat is still wild, and only calls
 * {@code setOwner} on success - so reaching the end of it already tamed means this is the
 * moment it happened.
 */
@Mixin(CatEntity.class)
public abstract class CatTameMixin {
	private static final float COLLAR_CHANCE = 0.10F;

	@Inject(method = "tryTame", at = @At("TAIL"))
	private void slickfun$maybeDropCollar(PlayerEntity player, CallbackInfo ci) {
		CatEntity cat = (CatEntity) (Object) this;

		if (!cat.isTamed() || !(cat.getWorld() instanceof ServerWorld world)) {
			return;
		}

		if (cat.getRandom().nextFloat() >= COLLAR_CHANCE) {
			return;
		}

		cat.dropStack(new ItemStack(ModItems.CAT_COLLAR));

		world.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getBodyY(1.0D), cat.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.0D);
		world.playSound(null, cat.getX(), cat.getY(), cat.getZ(),
				SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.0F, 1.0F);

		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(Text.translatable("message.slickfun.collar.dropped").formatted(Formatting.LIGHT_PURPLE), false);
		}
	}
}
