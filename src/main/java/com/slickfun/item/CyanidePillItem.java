package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModDamageTypes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * A snack-speed consumable that kills whoever swallows it. Cheap to craft on purpose - it is
 * meant to be the disposable "I need to be at spawn right now" option.
 */
public class CyanidePillItem extends Item {
	public CyanidePillItem(Settings settings) {
		super(settings);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		ItemStack result = super.finishUsing(stack, world, user);

		if (!world.isClient) {
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8F, 1.5F);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.7F, 0.6F);

			if (world instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.SMOKE,
						user.getX(), user.getBodyY(0.7D), user.getZ(), 25, 0.25D, 0.3D, 0.25D, 0.01D);
				serverWorld.spawnParticles(ParticleTypes.EFFECT,
						user.getX(), user.getBodyY(0.8D), user.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.0D);
			}

			user.damage(ModDamageTypes.source(world, ModDamageTypes.CYANIDE), Float.MAX_VALUE);
		}

		return result;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.cyanide_pill.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.cyanide_pill.2").formatted(Formatting.DARK_GRAY));
	}
}
