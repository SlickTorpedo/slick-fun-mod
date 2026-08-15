package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Toggle on, and loose items and XP within 7 blocks come to you. Works from anywhere in your inventory. */
public class ItemMagnetItem extends Item {
	public ItemMagnetItem(Settings settings) {
		super(settings);
	}

	public static boolean isActive(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MAGNET_ACTIVE, false);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		boolean nowActive = !isActive(stack);
		stack.set(ModComponents.MAGNET_ACTIVE, nowActive);

		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				nowActive ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
				SoundCategory.PLAYERS, 0.4F, 1.8F);

		user.sendMessage(Text.translatable(nowActive ? "message.slickfun.magnet.on" : "message.slickfun.magnet.off")
				.formatted(nowActive ? Formatting.GREEN : Formatting.GRAY), true);

		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.item_magnet").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(isActive(stack) ? "tooltip.slickfun.item_magnet.on" : "tooltip.slickfun.item_magnet.off")
				.formatted(isActive(stack) ? Formatting.GREEN : Formatting.DARK_GRAY));
	}
}
