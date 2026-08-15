package com.slickfun.item;

import java.util.List;

import com.slickfun.util.PrankManager;
import com.slickfun.util.ServerScheduler;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Things whose entire purpose is to make someone else say "what was that". */
public final class PrankItems {
	private PrankItems() {
	}

	private static void tip(List<Text> tooltip, String key) {
		tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
	}

	/**
	 * Launches someone straight up and gives them Slow Falling on the way, so the landing is
	 * a surprise rather than an injury.
	 */
	public static class BoopGlove extends Item {
		private static final int COOLDOWN_TICKS = 40;

		public BoopGlove(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
			if (user.getWorld().isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(user instanceof ServerPlayerEntity player) || !(user.getWorld() instanceof ServerWorld world)) {
				return ActionResult.PASS;
			}

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return ActionResult.FAIL;
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			entity.setVelocity(entity.getVelocity().x, 0.9D, entity.getVelocity().z);
			entity.velocityModified = true;
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false, true));

			world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0F, 1.6F);
			world.spawnParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY(), entity.getZ(),
					20, 0.3D, 0.1D, 0.3D, 0.05D);

			if (entity instanceof ServerPlayerEntity booped) {
				booped.sendMessage(Text.translatable("message.slickfun.boop.booped", player.getName())
						.formatted(Formatting.YELLOW), true);
			}

			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.boop_glove");
		}
	}

	/** Pure noise and colour. Harmless, and hard to ignore. */
	public static class ConfettiCannon extends Item {
		private static final int COOLDOWN_TICKS = 60;
		private static final int BURSTS = 6;

		public ConfettiCannon(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
			if (user.getWorld().isClient) {
				return ActionResult.SUCCESS;
			}

			if (user instanceof ServerPlayerEntity player && !fire(player, entity.getX(), entity.getY(), entity.getZ())) {
				return ActionResult.FAIL;
			}

			return ActionResult.SUCCESS;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (user instanceof ServerPlayerEntity player && fire(player, user.getX(), user.getY(), user.getZ())) {
				return TypedActionResult.success(stack);
			}

			return TypedActionResult.fail(stack);
		}

		private boolean fire(ServerPlayerEntity player, double x, double y, double z) {
			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return false;
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			ServerWorld world = player.getServerWorld();

			for (int burst = 0; burst < BURSTS; burst++) {
				ServerScheduler.schedule(burst * 4, () -> {
					for (int puff = 0; puff < 8; puff++) {
						Vector3f colour = new Vector3f(world.getRandom().nextFloat(),
								world.getRandom().nextFloat(), world.getRandom().nextFloat());
						world.spawnParticles(new DustParticleEffect(colour, 1.6F),
								x, y + 1.2D, z, 12, 0.8D, 0.8D, 0.8D, 0.2D);
					}

					world.spawnParticles(ParticleTypes.FIREWORK, x, y + 1.5D, z, 30, 0.5D, 0.5D, 0.5D, 0.3D);
					world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
							SoundCategory.PLAYERS, 1.0F, 1.0F + world.getRandom().nextFloat() * 0.6F);
				});
			}

			return true;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.confetti_cannon");
		}
	}

	/**
	 * Makes a mob shriek at random for a minute.
	 *
	 * <p>The sound plays at the animal rather than at whoever set it off, which is the entire
	 * joke: the noise follows the cow around someone else's base.
	 */
	public static class ScreamingSheep extends Item {
		private static final int SCREAMS = 8;
		private static final int SPREAD_TICKS = 20 * 60;

		private static final SoundEvent[] NOISES = {
				SoundEvents.ENTITY_GOAT_SCREAMING_AMBIENT,
				SoundEvents.ENTITY_GOAT_SCREAMING_DEATH,
				SoundEvents.ENTITY_SHEEP_AMBIENT,
				SoundEvents.ENTITY_DONKEY_ANGRY
		};

		public ScreamingSheep(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
			if (user.getWorld().isClient) {
				return ActionResult.SUCCESS;
			}

			if (entity instanceof PlayerEntity || !(user.getWorld() instanceof ServerWorld world)) {
				return ActionResult.PASS;
			}

			for (int scream = 0; scream < SCREAMS; scream++) {
				int delay = world.getRandom().nextInt(SPREAD_TICKS);

				ServerScheduler.schedule(delay, () -> {
					if (!entity.isAlive()) {
						return;
					}

					SoundEvent noise = NOISES[world.getRandom().nextInt(NOISES.length)];
					world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
							noise, SoundCategory.NEUTRAL, 1.4F, 0.6F + world.getRandom().nextFloat() * 0.8F);
				});
			}

			user.sendMessage(Text.translatable("message.slickfun.scream.armed", entity.getName())
					.formatted(Formatting.GOLD), true);
			stack.decrement(1);

			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.screaming_sheep");
		}
	}

	/**
	 * Passed by right clicking someone. Whoever is holding it when the fuse runs out gets a
	 * bang and a shove - no damage, no block breaking, just a very public moment.
	 */
	public static class HotPotato extends Item {
		public HotPotato(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
			if (user.getWorld().isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(user instanceof ServerPlayerEntity thrower) || !(entity instanceof ServerPlayerEntity catcher)) {
				return ActionResult.PASS;
			}

			PrankManager.passPotato(thrower, catcher, stack, hand);
			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.hot_potato", PrankManager.FUSE_TICKS / 20)
					.formatted(Formatting.GRAY));
		}
	}

	/**
	 * Looks exactly like a diamond in a chest, because that is the whole item. Using it is how
	 * you find out, and by then someone is already laughing.
	 */
	public static class FakeDiamond extends Item {
		public FakeDiamond(Settings settings) {
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

			stack.decrement(1);

			if (!player.getInventory().insertStack(new ItemStack(Items.GRAVEL))) {
				player.dropItem(new ItemStack(Items.GRAVEL), false);
			}

			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0F, 0.7F);
			player.getServerWorld().spawnParticles(ParticleTypes.SMOKE,
					player.getX(), player.getY() + 1.2D, player.getZ(), 20, 0.3D, 0.3D, 0.3D, 0.02D);
			player.sendMessage(Text.translatable("message.slickfun.fake_diamond").formatted(Formatting.GRAY), true);

			return TypedActionResult.success(stack);
		}
	}
}
