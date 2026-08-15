package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

/**
 * Items that look like they do something. Two of them genuinely don't; two do something,
 * just not the thing on the tin.
 */
public final class JokeItems {
	private JokeItems() {
	}

	/** Reads like a mysterious artefact. Is a rock. Right click and you drop everything. */
	public static class HeavyStone extends Item {
		public HeavyStone(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			user.sendMessage(Text.translatable("message.slickfun.heavy_stone").formatted(Formatting.RED), false);
			user.getInventory().dropAll();

			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_PLAYER_BIG_FALL, SoundCategory.PLAYERS, 1.0F, 0.8F);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_STONE_FALL, SoundCategory.PLAYERS, 1.0F, 0.6F);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.heavy_stone.1").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
			tooltip.add(Text.translatable("tooltip.slickfun.heavy_stone.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Holds an item in your off hand. Fails. Encourages you to try again. Always fails. */
	public static class ItemDuper extends Item {
		public ItemDuper(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (hand != Hand.MAIN_HAND) {
				return TypedActionResult.pass(stack);
			}

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			ItemStack target = user.getOffHandStack();

			if (target.isEmpty()) {
				user.sendMessage(Text.translatable("message.slickfun.duper.empty").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			user.sendMessage(Text.translatable("message.slickfun.duper.failed", target.getName())
					.formatted(Formatting.YELLOW), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.PLAYERS, 1.0F, 0.5F);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8F, 0.8F);

			user.getItemCooldownManager().set(this, 30);
			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.item_duper.1").formatted(Formatting.GREEN));
			tooltip.add(Text.translatable("tooltip.slickfun.item_duper.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Changes the channel. There are no televisions in Minecraft. */
	public static class TvRemote extends Item {
		private static final int CHANNELS = 99;

		public TvRemote(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			int channel = stack.getOrDefault(ModComponents.TV_CHANNEL, 1) % CHANNELS + 1;
			stack.set(ModComponents.TV_CHANNEL, channel);

			user.sendMessage(Text.translatable("message.slickfun.remote.channel", channel).formatted(Formatting.GRAY), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.4F, 1.8F);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.tv_remote.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.tv_remote.2",
					stack.getOrDefault(ModComponents.TV_CHANNEL, 1)).formatted(Formatting.DARK_GRAY));
		}
	}

	/** Extremely dark. Right click to put them on, right click to take them off. */
	public static class DarkShades extends Item {
		public DarkShades(Settings settings) {
			super(settings);
		}

		public static boolean isWorn(ItemStack stack) {
			return stack.getOrDefault(ModComponents.SHADES_WORN, false);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			boolean worn = !isWorn(stack);
			stack.set(ModComponents.SHADES_WORN, worn);

			if (!worn && user instanceof LivingEntity living) {
				living.removeStatusEffect(StatusEffects.BLINDNESS);
			}

			user.sendMessage(Text.translatable(worn ? "message.slickfun.shades.on" : "message.slickfun.shades.off")
					.formatted(Formatting.GRAY), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.6F, 1.2F);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
			if (world.isClient || !isWorn(stack) || !(entity instanceof LivingEntity living)) {
				return;
			}

			// Refreshed constantly so it never quite runs out while they are on.
			if (world.getTime() % 20L == 0L) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false, true));
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.dark_shades.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable(isWorn(stack) ? "tooltip.slickfun.dark_shades.on" : "tooltip.slickfun.dark_shades.off")
					.formatted(Formatting.DARK_GRAY));
		}
	}
}
