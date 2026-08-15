package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModDamageTypes;
import com.slickfun.util.ServerScheduler;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Weapons that only ever work on other players. Animals are safe from all of these. */
public final class WeaponItems {
	private WeaponItems() {
	}

	/** Finds the first player on a ray, optionally straight through walls. */
	private static ServerPlayerEntity trace(ServerWorld level, ServerPlayerEntity shooter, Vec3d from, Vec3d aim,
			double range, double step, double hitRadius, boolean throughWalls, java.util.function.Consumer<Vec3d> trail) {
		for (double travelled = 0.0D; travelled < range; travelled += step) {
			Vec3d point = from.add(aim.multiply(travelled));

			if (travelled > 0.6D && trail != null) {
				trail.accept(point);
			}

			if (!throughWalls) {
				BlockPos block = BlockPos.ofFloored(point);

				if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
					return null;
				}
			}

			Box around = new Box(point, point).expand(hitRadius);

			for (ServerPlayerEntity candidate : level.getEntitiesByClass(ServerPlayerEntity.class, around,
					other -> other != shooter && other.isAlive() && !other.isSpectator() && !other.isCreative())) {
				return candidate;
			}
		}

		return null;
	}

	/**
	 * A narrow red beam that kills a player outright.
	 *
	 * <p>The damage bypasses armour through its own damage type rather than being a very large
	 * number, so a full set of netherite is no more use than nothing. A minute between shots is
	 * what keeps it from simply deciding every fight.
	 */
	public static class RailGun extends Item {
		private static final int COOLDOWN_TICKS = 20 * 60;
		private static final double RANGE = 120.0D;

		public RailGun(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity shooter) || !(world instanceof ServerWorld level)) {
				return TypedActionResult.fail(stack);
			}

			if (shooter.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			shooter.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			Vec3d muzzle = shooter.getEyePos();
			Vec3d aim = shooter.getRotationVec(1.0F);
			DustParticleEffect beam = new DustParticleEffect(new Vector3f(1.0F, 0.05F, 0.05F), 0.7F);

			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
					SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.6F, 1.8F);
			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.4F, 2.0F);

			ServerPlayerEntity hit = trace(level, shooter, muzzle, aim, RANGE, 0.2D, 0.6D, false,
					point -> level.spawnParticles(beam, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D));

			if (hit == null) {
				return TypedActionResult.success(stack);
			}

			level.spawnParticles(ParticleTypes.EXPLOSION, hit.getX(), hit.getBodyY(0.6D), hit.getZ(),
					3, 0.2D, 0.3D, 0.2D, 0.0D);
			hit.damage(ModDamageTypes.source(level, ModDamageTypes.RAILGUN), Float.MAX_VALUE);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.rail_gun").formatted(Formatting.RED));
			tooltip.add(Text.translatable("tooltip.slickfun.rail_gun.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/**
	 * Ten blocks of reach, and players only.
	 *
	 * <p>The reach itself is a real attribute modifier so vanilla's own attack handling accepts
	 * the swing; the players-only rule is enforced in {@code WeaponManager}, which cancels the
	 * hit on anything else. Fed enough ender pearls, a right click strikes through walls -
	 * something the ordinary swing can never do, because the client will not send an attack it
	 * cannot see a path to.
	 */
	public static class SwordOfLongArms extends SwordItem {
		public static final int REACH = 10;
		public static final int PEARL_COST = 64;

		private static final int COOLDOWN_TICKS = 30;
		private static final float DAMAGE = 7.0F;

		public SwordOfLongArms(Settings settings) {
			super(ToolMaterials.NETHERITE, settings);
		}

		public static int pearlsIn(ItemStack stack) {
			return stack.getOrDefault(ModComponents.PEARL_CHARGE, 0);
		}

		public static boolean seesThroughWalls(ItemStack stack) {
			return pearlsIn(stack) >= PEARL_COST;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld level)) {
				return TypedActionResult.fail(stack);
			}

			if (player.isSneaking()) {
				return feed(player, stack);
			}

			return strike(level, player, stack) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
		}

		/**
		 * Called from the swing mixin, so a left click works too.
		 *
		 * <p>This is the fix for "it does not hit through walls": a left click at someone behind
		 * a wall never reaches the server as an attack at all, because the client refuses to
		 * send one for a target it cannot see. Only the swing itself arrives.
		 */
		public static void onSwing(ServerPlayerEntity player, Hand hand) {
			ItemStack held = player.getStackInHand(hand);

			if (held.getItem() instanceof SwordOfLongArms sword && !player.isSneaking()) {
				sword.strike(player.getServerWorld(), player, held);
			}
		}

		private boolean strike(ServerWorld level, ServerPlayerEntity player, ItemStack stack) {
			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return false;
			}

			boolean throughWalls = seesThroughWalls(stack);
			ServerPlayerEntity target = trace(level, player, player.getEyePos(), player.getRotationVec(1.0F),
					REACH, 0.25D, 0.7D, throughWalls, null);

			if (target == null) {
				return false;
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			target.timeUntilRegen = 0;
			target.damage(level.getDamageSources().playerAttack(player), DAMAGE);

			level.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.6D), target.getZ(),
					2, 0.2D, 0.2D, 0.2D, 0.0D);
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.8F);

			if (throughWalls) {
				drawReach(level, player.getEyePos(), target.getPos().add(0.0D, 1.0D, 0.0D));
			}

			return true;
		}

		private static void drawReach(ServerWorld level, Vec3d from, Vec3d to) {
			Vec3d delta = to.subtract(from);
			int points = (int) Math.min(60.0D, delta.length() * 4.0D);

			for (int i = 1; i <= points; i++) {
				Vec3d point = from.add(delta.multiply((double) i / points));
				level.spawnParticles(ParticleTypes.PORTAL, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}

		private static TypedActionResult<ItemStack> feed(ServerPlayerEntity player, ItemStack stack) {
			int wanted = PEARL_COST - pearlsIn(stack);

			if (wanted <= 0) {
				player.sendMessage(Text.translatable("message.slickfun.longarms.already").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			int taken = 0;

			for (int slot = 0; slot < player.getInventory().size() && taken < wanted; slot++) {
				ItemStack held = player.getInventory().getStack(slot);

				if (!held.isOf(Items.ENDER_PEARL)) {
					continue;
				}

				int moved = Math.min(wanted - taken, held.getCount());
				held.decrement(moved);
				taken += moved;

				if (held.isEmpty()) {
					player.getInventory().setStack(slot, ItemStack.EMPTY);
				}
			}

			if (taken == 0) {
				player.sendMessage(Text.translatable("message.slickfun.longarms.need", wanted).formatted(Formatting.GRAY), false);
				return TypedActionResult.fail(stack);
			}

			stack.set(ModComponents.PEARL_CHARGE, pearlsIn(stack) + taken);
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 0.6F);
			player.sendMessage(Text.translatable(seesThroughWalls(stack)
							? "message.slickfun.longarms.charged"
							: "message.slickfun.longarms.fed", pearlsIn(stack), PEARL_COST)
					.formatted(Formatting.LIGHT_PURPLE), false);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.long_arms", REACH).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.long_arms.2").formatted(Formatting.DARK_GRAY));

			if (seesThroughWalls(stack)) {
				tooltip.add(Text.translatable("tooltip.slickfun.long_arms.walls").formatted(Formatting.LIGHT_PURPLE));
			} else {
				tooltip.add(Text.translatable("tooltip.slickfun.long_arms.pearls", pearlsIn(stack), PEARL_COST)
						.formatted(Formatting.DARK_GRAY));
			}
		}
	}

	/**
	 * A bucket of something white and unpleasant, thrown over someone's screen.
	 *
	 * <p>The particles are sent only to the victim, and spawned right on top of their camera,
	 * which is the closest a server-side mod can get to painting on someone's screen. Everyone
	 * else sees a normal player standing there wondering what happened.
	 */
	public static class BucketOfGoon extends Item {
		private static final int WAVES = 14;
		private static final int WAVE_TICKS = 6;

		/** How long drinking it keeps harmful effects off you. */
		public static final int IMMUNITY_TICKS = 20 * 60 * 5;

		public BucketOfGoon(Settings settings) {
			super(settings);
		}

		// Right click at a player throws it; right click at nothing drinks it.

		@Override
		public net.minecraft.util.UseAction getUseAction(ItemStack stack) {
			return net.minecraft.util.UseAction.DRINK;
		}

		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return 40;
		}

		@Override
		public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
			ItemStack left = super.finishUsing(stack, world, user);

			if (!world.isClient && user instanceof ServerPlayerEntity drinker) {
				com.slickfun.util.GoonManager.grant(drinker, IMMUNITY_TICKS);

				if (!drinker.getInventory().insertStack(new ItemStack(Items.BUCKET))) {
					drinker.dropItem(new ItemStack(Items.BUCKET), false);
				}
			}

			return left;
		}

		@Override
		public net.minecraft.util.ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
			if (user.getWorld().isClient) {
				return net.minecraft.util.ActionResult.SUCCESS;
			}

			if (!(user instanceof ServerPlayerEntity thrower) || !(entity instanceof ServerPlayerEntity victim)) {
				return net.minecraft.util.ActionResult.PASS;
			}

			ServerWorld level = victim.getServerWorld();

			for (int wave = 0; wave < WAVES; wave++) {
				ServerScheduler.schedule(wave * WAVE_TICKS, () -> splatter(level, victim));
			}

			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
					SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.4F, 0.6F);

			victim.sendMessage(Text.translatable("message.slickfun.goon.hit", thrower.getName())
					.formatted(Formatting.WHITE), false);
			thrower.sendMessage(Text.translatable("message.slickfun.goon.thrown", victim.getName())
					.formatted(Formatting.GRAY), true);

			stack.decrement(1);

			if (!thrower.getInventory().insertStack(new ItemStack(Items.BUCKET))) {
				thrower.dropItem(new ItemStack(Items.BUCKET), false);
			}

			return net.minecraft.util.ActionResult.SUCCESS;
		}

		/** Right on the camera, and only for them. */
		private static void splatter(ServerWorld level, ServerPlayerEntity victim) {
			if (victim.isRemoved()) {
				return;
			}

			Vec3d eyes = victim.getEyePos();

			level.spawnParticles(victim, ParticleTypes.SPIT, true, eyes.x, eyes.y, eyes.z, 60, 0.4D, 0.4D, 0.4D, 0.02D);
			level.spawnParticles(victim, ParticleTypes.SNOWFLAKE, true, eyes.x, eyes.y, eyes.z, 40, 0.3D, 0.3D, 0.3D, 0.01D);
			level.spawnParticles(victim, ParticleTypes.WHITE_ASH, true, eyes.x, eyes.y, eyes.z, 50, 0.5D, 0.5D, 0.5D, 0.0D);

			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
					SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.PLAYERS,
					1.0F, 0.5F + level.getRandom().nextFloat() * 0.5F);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.bucket_of_goon").formatted(Formatting.GRAY));
		}
	}
}
