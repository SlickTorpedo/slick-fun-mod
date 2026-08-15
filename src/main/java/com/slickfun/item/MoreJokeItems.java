package com.slickfun.item;

import java.util.List;

import com.slickfun.util.ServerScheduler;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Round two of things that look like they do something. */
public final class MoreJokeItems {
	private MoreJokeItems() {
	}

	/** Squeaks. */
	public static class RubberDuck extends Item {
		public RubberDuck(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (!world.isClient) {
				world.playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.PLAYERS, 0.7F, 2.0F);
				ServerScheduler.schedule(4, () -> world.playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.PLAYERS, 0.5F, 1.9F));
				user.getItemCooldownManager().set(this, 10);
			}

			return TypedActionResult.success(stack, world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.rubber_duck").formatted(Formatting.GRAY));
		}
	}

	/** Endless encouragement, none of it useful. */
	public static class MotivationalPoster extends Item {
		private static final int LINES = 12;

		public MotivationalPoster(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (!world.isClient) {
				int line = world.getRandom().nextInt(LINES) + 1;
				user.sendMessage(Text.translatable("message.slickfun.poster." + line)
						.formatted(Formatting.GOLD, Formatting.ITALIC), false);
				world.playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.7F, 1.0F);
				user.getItemCooldownManager().set(this, 20);
			}

			return TypedActionResult.success(stack, world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.motivational_poster").formatted(Formatting.GRAY));
		}
	}

	/** Points somewhere. Not anywhere useful. Repoints every few seconds. */
	public static class BrokenCompass extends Item {
		private static final int REROLL_TICKS = 60;
		private static final int WANDER = 2000;

		public BrokenCompass(Settings settings) {
			super(settings);
		}

		@Override
		public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
			if (world.isClient || world.getTime() % REROLL_TICKS != 0L) {
				return;
			}

			Random random = world.getRandom();
			BlockPos nonsense = entity.getBlockPos().add(
					random.nextInt(WANDER * 2) - WANDER, 0, random.nextInt(WANDER * 2) - WANDER);

			stack.set(DataComponentTypes.LODESTONE_TRACKER,
					new LodestoneTrackerComponent(java.util.Optional.of(GlobalPos.create(world.getRegistryKey(), nonsense)), false));
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.broken_compass.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.broken_compass.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Ten seconds of escalating beeping, then nothing at all. */
	public static class SelfDestructButton extends Item {
		private static final int COUNT_FROM = 10;

		public SelfDestructButton(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player)) {
				return TypedActionResult.fail(stack);
			}

			player.sendMessage(Text.translatable("message.slickfun.destruct.armed").formatted(Formatting.RED, Formatting.BOLD), false);

			for (int count = COUNT_FROM; count > 0; count--) {
				int remaining = count;
				ServerScheduler.schedule((COUNT_FROM - count) * 20 + 1, () -> {
					if (player.isRemoved()) {
						return;
					}

					player.sendMessage(Text.literal(String.valueOf(remaining)).formatted(Formatting.RED, Formatting.BOLD), true);
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 1.0F,
							0.5F + (COUNT_FROM - remaining) * 0.12F);
				});
			}

			ServerScheduler.schedule(COUNT_FROM * 20 + 20, () -> {
				if (!player.isRemoved()) {
					player.sendMessage(Text.translatable("message.slickfun.destruct.nothing").formatted(Formatting.GRAY), false);
				}
			});

			user.getItemCooldownManager().set(this, COUNT_FROM * 20 + 60);
			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.self_destruct.1").formatted(Formatting.RED));
			tooltip.add(Text.translatable("tooltip.slickfun.self_destruct.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Genuinely random, including the bad ones. */
	public static class MysteryStew extends Item {
		private static final List<RegistryEntry<net.minecraft.entity.effect.StatusEffect>> POOL = List.of(
				StatusEffects.SPEED, StatusEffects.STRENGTH, StatusEffects.REGENERATION,
				StatusEffects.JUMP_BOOST, StatusEffects.NIGHT_VISION, StatusEffects.FIRE_RESISTANCE,
				StatusEffects.WATER_BREATHING, StatusEffects.LUCK, StatusEffects.ABSORPTION,
				StatusEffects.SLOWNESS, StatusEffects.NAUSEA, StatusEffects.BLINDNESS,
				StatusEffects.HUNGER, StatusEffects.WEAKNESS, StatusEffects.LEVITATION,
				StatusEffects.GLOWING);

		public MysteryStew(Settings settings) {
			super(settings);
		}

		@Override
		public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
			ItemStack result = super.finishUsing(stack, world, user);

			if (!world.isClient) {
				RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect =
						POOL.get(world.getRandom().nextInt(POOL.size()));
				user.addStatusEffect(new StatusEffectInstance(effect, 20 * 20, 0, false, true, true));

				if (user instanceof ServerPlayerEntity player) {
					player.sendMessage(Text.translatable("message.slickfun.stew").formatted(Formatting.GRAY), true);
				}
			}

			return result;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.mystery_stew.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.mystery_stew.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Bottomless. Also open at the bottom. */
	public static class EmptyBagOfHolding extends Item {
		public EmptyBagOfHolding(Settings settings) {
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

			ItemStack offering = user.getOffHandStack();

			if (offering.isEmpty()) {
				user.sendMessage(Text.translatable("message.slickfun.bag.empty").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			ItemStack lost = offering.copy();
			offering.setCount(0);
			user.dropItem(lost, false);

			user.sendMessage(Text.translatable("message.slickfun.bag.through", lost.getName()).formatted(Formatting.YELLOW), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.6F, 0.6F);

			user.getItemCooldownManager().set(this, 20);
			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.bag_of_holding.1").formatted(Formatting.LIGHT_PURPLE));
			tooltip.add(Text.translatable("tooltip.slickfun.bag_of_holding.2").formatted(Formatting.DARK_GRAY));
		}
	}
}
