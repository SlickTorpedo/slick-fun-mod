package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.util.ChestSearch;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Things you carry that quietly do their job. */
public final class CharmTools {
	private CharmTools() {
	}

	/** Speed and jump for everyone close enough to be travelling with you. */
	public static class PartyHorn extends Item {
		private static final double RADIUS = 48.0D;
		private static final int DURATION = 20 * 30;
		private static final int COOLDOWN_TICKS = 20 * 60;

		public PartyHorn(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			int reached = 0;

			for (ServerPlayerEntity nearby : serverWorld.getPlayers(p -> p.squaredDistanceTo(player) < RADIUS * RADIUS)) {
				nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, DURATION, 1, false, true, true));
				nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, DURATION, 1, false, true, true));
				nearby.sendMessage(Text.translatable("message.slickfun.party", player.getDisplayName()).formatted(Formatting.LIGHT_PURPLE), false);
				reached++;
			}

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 1.0F, 1.8F);
			serverWorld.spawnParticles(ParticleTypes.NOTE, player.getX(), player.getBodyY(1.2D), player.getZ(),
					40, 1.0D, 0.6D, 1.0D, 1.0D);

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			player.sendMessage(Text.translatable("message.slickfun.party.count", reached).formatted(Formatting.GRAY), true);
			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.party_horn.1", (int) RADIUS).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.party_horn.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Luck while you carry it, which is what actually improves fishing loot. */
	public static class FishersCharm extends Item {
		public FishersCharm(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.fishers_charm.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.fishers_charm.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Puts one free block on the shield in your off hand. */
	public static class ShieldCharger extends Item {
		private static final int COOLDOWN_TICKS = 20 * 45;

		public ShieldCharger(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			ItemStack shield = user.getOffHandStack();

			if (!shield.isOf(Items.SHIELD)) {
				user.sendMessage(Text.translatable("message.slickfun.charger.no_shield").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			if (shield.getOrDefault(ModComponents.SHIELD_CHARGED, false)) {
				user.sendMessage(Text.translatable("message.slickfun.charger.already").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			shield.set(ModComponents.SHIELD_CHARGED, true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.4F);

			if (world instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.ENCHANT, user.getX(), user.getBodyY(1.0D), user.getZ(),
						30, 0.4D, 0.4D, 0.4D, 0.5D);
			}

			user.sendMessage(Text.translatable("message.slickfun.charger.charged").formatted(Formatting.AQUA), true);
			user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.shield_charger.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.shield_charger.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Points at the nearest instance of whatever biome you told it to find. */
	public static class BiomeCompass extends Item {
		private static final int SEARCH_RADIUS = 3200;
		private static final int COOLDOWN_TICKS = 100;

		public BiomeCompass(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			// Sneak to lock in whatever biome you are standing in.
			if (player.isSneaking()) {
				Identifier here = serverWorld.getRegistryManager().get(RegistryKeys.BIOME)
						.getId(serverWorld.getBiome(player.getBlockPos()).value());

				if (here == null) {
					return TypedActionResult.fail(stack);
				}

				stack.set(ModComponents.TARGET_BIOME, here);
				stack.remove(DataComponentTypes.LODESTONE_TRACKER);
				player.sendMessage(Text.translatable("message.slickfun.biome.set", here.getPath()).formatted(Formatting.AQUA), true);
				return TypedActionResult.success(stack, false);
			}

			Identifier wanted = stack.get(ModComponents.TARGET_BIOME);

			if (wanted == null) {
				player.sendMessage(Text.translatable("message.slickfun.biome.unset").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			player.sendMessage(Text.translatable("message.slickfun.biome.searching", wanted.getPath()).formatted(Formatting.GRAY), true);

			// This walks a lot of chunks worth of noise; the cooldown is what keeps it sane.
			var found = serverWorld.locateBiome(
					entry -> entry.matchesId(wanted), player.getBlockPos(), SEARCH_RADIUS, 32, 64);

			if (found == null) {
				player.sendMessage(Text.translatable("message.slickfun.biome.not_found", wanted.getPath()).formatted(Formatting.GRAY), false);
				return TypedActionResult.fail(stack);
			}

			BlockPos where = found.getFirst();
			stack.set(DataComponentTypes.LODESTONE_TRACKER, new net.minecraft.component.type.LodestoneTrackerComponent(
					java.util.Optional.of(net.minecraft.util.math.GlobalPos.create(serverWorld.getRegistryKey(), where)), false));

			player.sendMessage(Text.translatable("message.slickfun.biome.found",
					wanted.getPath(), where.getX(), where.getZ(),
					(int) Math.sqrt(where.getSquaredDistance(player.getPos()))).formatted(Formatting.AQUA), false);
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.7F, 1.4F);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			Identifier wanted = stack.get(ModComponents.TARGET_BIOME);

			tooltip.add(Text.translatable("tooltip.slickfun.biome_compass.1").formatted(Formatting.GRAY));
			tooltip.add(wanted == null
					? Text.translatable("tooltip.slickfun.biome_compass.unset").formatted(Formatting.DARK_GRAY)
					: Text.translatable("tooltip.slickfun.biome_compass.set", wanted.getPath()).formatted(Formatting.AQUA));
		}
	}

	/**
	 * Deletes junk as you pick it up.
	 *
	 * <p>The filter is a list of sample stacks kept in the item's own container component, so
	 * it travels with the filter and survives being handed on.
	 */
	public static class VoidFilter extends Item {
		public static final int SLOTS = 9;

		public VoidFilter(Settings settings) {
			super(settings);
		}

		public static DefaultedList<ItemStack> filterOf(ItemStack stack) {
			DefaultedList<ItemStack> entries = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
			stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(entries);
			return entries;
		}

		public static boolean filters(ItemStack filter, ItemStack candidate) {
			for (ItemStack entry : filterOf(filter)) {
				if (!entry.isEmpty() && entry.isOf(candidate.getItem())) {
					return true;
				}
			}

			return false;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (user instanceof ServerPlayerEntity player) {
				com.slickfun.screen.VoidFilterScreenHandler.open(player, stack, hand);
			}

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.void_filter.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.void_filter.2").formatted(Formatting.DARK_GRAY));

			for (ItemStack entry : filterOf(stack)) {
				if (!entry.isEmpty()) {
					tooltip.add(Text.literal(" - ").formatted(Formatting.DARK_GRAY)
							.append(entry.getName().copy().formatted(Formatting.RED)));
				}
			}
		}
	}

	/** Shoves hostile mobs away while it is switched on. Does not stop them spawning. */
	public static class MobRepellent extends Item {
		public static final double RADIUS = 12.0D;

		public MobRepellent(Settings settings) {
			super(settings);
		}

		public static boolean isActive(ItemStack stack) {
			return stack.getOrDefault(ModComponents.REPELLENT_ACTIVE, false);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			boolean on = !isActive(stack);
			stack.set(ModComponents.REPELLENT_ACTIVE, on);

			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					on ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
					SoundCategory.PLAYERS, 0.5F, 1.4F);
			user.sendMessage(Text.translatable(on ? "message.slickfun.repellent.on" : "message.slickfun.repellent.off")
					.formatted(on ? Formatting.GREEN : Formatting.GRAY), true);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.mob_repellent.1", (int) RADIUS).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable(isActive(stack) ? "tooltip.slickfun.mob_repellent.on" : "tooltip.slickfun.mob_repellent.off")
					.formatted(isActive(stack) ? Formatting.GREEN : Formatting.DARK_GRAY));
		}
	}
}
