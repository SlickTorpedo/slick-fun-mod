package com.slickfun.item;

import java.util.List;

import com.slickfun.util.Ballistics;
import com.slickfun.util.RpgManager;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * The rest of the armoury.
 *
 * <p>Two shapes cover all of it. {@link Automatic} keeps firing for as long as the trigger is
 * held, using the same hold-to-use mechanism a bow draws with; {@link SingleShot} fires once
 * per click and leans on the item cooldown. Everything else is numbers.
 *
 * <p>All of them hit anything alive rather than players only - the players-only rule belongs
 * to the Sword of Long Arms and the Rail Gun, not to ordinary guns.
 */
public final class MoreGuns {
	private MoreGuns() {
	}

	// ------------------------------------------------------------------ shared shapes

	/**
	 * A gun that runs while the trigger is held.
	 *
	 * <p>Minecraft has no "is the button down" flag to read, but it does have hold-to-use, and
	 * {@code usageTick} runs every tick until the button comes up. Counting ticks in there is
	 * what turns one long press into a stream of shots.
	 */
	public abstract static class Automatic extends Item {
		protected Automatic(Settings settings) {
			super(settings);
		}

		/** Ticks between shots. 1 is as fast as the game runs. */
		protected abstract int interval();

		protected abstract void shoot(ServerWorld level, ServerPlayerEntity shooter, int heldTicks);

		/** Ticks of trigger held before anything comes out. Spin-up, for the minigun. */
		protected int windUp() {
			return 0;
		}

		protected void onWindUp(ServerWorld level, ServerPlayerEntity shooter) {
		}

		@Override
		public UseAction getUseAction(ItemStack stack) {
			return UseAction.BOW;
		}

		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			if (!world.isClient && world instanceof ServerWorld level && user instanceof ServerPlayerEntity shooter
					&& windUp() > 0) {
				onWindUp(level, shooter);
			}

			return ItemUsage.consumeHeldItem(world, user, hand);
		}

		@Override
		public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
			if (world.isClient || !(world instanceof ServerWorld level) || !(user instanceof ServerPlayerEntity shooter)) {
				return;
			}

			int held = getMaxUseTime(stack, user) - remainingUseTicks;

			if (held < windUp() || (held - windUp()) % interval() != 0) {
				return;
			}

			shoot(level, shooter, held);
		}
	}

	/** A gun that fires once per click, paced by the item cooldown. */
	public abstract static class SingleShot extends Item {
		protected SingleShot(Settings settings) {
			super(settings);
		}

		protected abstract int cooldown();

		protected abstract void shoot(ServerWorld level, ServerPlayerEntity shooter);

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

			shooter.getItemCooldownManager().set(this, cooldown());
			shoot(level, shooter);

			return TypedActionResult.success(stack);
		}
	}

	private static void report(ServerWorld level, ServerPlayerEntity shooter, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}

	private static void tip(List<Text> tooltip, String key) {
		tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
	}

	// ------------------------------------------------------------------ automatics

	/** Fast, close, and not especially accurate. */
	public static class Smg extends Automatic {
		public Smg(Settings settings) {
			super(settings);
		}

		@Override
		protected int interval() {
			return 2;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter, int heldTicks) {
			Vec3d aim = Ballistics.spread(level, shooter.getRotationVec(1.0F), 0.09D);
			report(level, shooter, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.4F, 2.0F);

			for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(), aim,
					32.0D, 0.4D, 0.7D, 1, ParticleTypes.CRIT)) {
				Ballistics.hurt(level, shooter, hit, 2.5F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.smg");
		}
	}

	/** Spins up, then does not stop. */
	public static class Minigun extends Automatic {
		public Minigun(Settings settings) {
			super(settings);
		}

		@Override
		protected int interval() {
			return 1;
		}

		@Override
		protected int windUp() {
			return 20;
		}

		@Override
		protected void onWindUp(ServerWorld level, ServerPlayerEntity shooter) {
			report(level, shooter, SoundEvents.BLOCK_PISTON_EXTEND, 1.4F, 0.5F);
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter, int heldTicks) {
			Vec3d aim = Ballistics.spread(level, shooter.getRotationVec(1.0F), 0.14D);
			report(level, shooter, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.35F, 2.0F);

			for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(), aim,
					40.0D, 0.4D, 0.7D, 1, ParticleTypes.CRIT)) {
				Ballistics.hurt(level, shooter, hit, 2.5F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.minigun");
		}
	}

	/** A short cone of fire. Sets everything in it alight. */
	public static class Flamethrower extends Automatic {
		private static final double RANGE = 9.0D;

		public Flamethrower(Settings settings) {
			super(settings);
		}

		@Override
		protected int interval() {
			return 2;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter, int heldTicks) {
			Vec3d eyes = shooter.getEyePos();
			report(level, shooter, SoundEvents.ITEM_FIRECHARGE_USE, 0.8F, 1.6F);

			// A handful of diverging rays rather than one, which is what makes it a cone.
			for (int jet = 0; jet < 5; jet++) {
				Vec3d aim = Ballistics.spread(level, shooter.getRotationVec(1.0F), 0.35D);

				for (double travelled = 1.0D; travelled < RANGE; travelled += 0.8D) {
					Vec3d point = eyes.add(aim.multiply(travelled));
					level.spawnParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 2, 0.15D, 0.15D, 0.15D, 0.01D);
				}

				for (LivingEntity hit : Ballistics.fire(level, shooter, eyes, aim, RANGE, 0.5D, 1.0D, 3, null)) {
					Ballistics.hurt(level, shooter, hit, 2.0F);
					hit.setOnFireFor(5);
				}
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.flamethrower");
		}
	}

	/** A held beam that cuts through everything standing in it. */
	public static class LaserRifle extends Automatic {
		public LaserRifle(Settings settings) {
			super(settings);
		}

		@Override
		protected int interval() {
			return 2;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter, int heldTicks) {
			ParticleEffect beam = new DustParticleEffect(new Vector3f(0.2F, 1.0F, 0.4F), 0.8F);
			report(level, shooter, SoundEvents.BLOCK_BEACON_AMBIENT, 0.7F, 2.0F);

			// Nothing stops it, so the target count is set high rather than to one.
			for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(),
					shooter.getRotationVec(1.0F), 48.0D, 0.3D, 0.7D, 16, beam)) {
				Ballistics.hurt(level, shooter, hit, 3.0F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.laser_rifle");
		}
	}

	// ------------------------------------------------------------------ single shots

	/** Eight pellets at once, spreading with distance. Devastating up close, useless far off. */
	public static class Shotgun extends SingleShot {
		private static final int PELLETS = 8;

		public Shotgun(Settings settings) {
			super(settings);
		}

		@Override
		protected int cooldown() {
			return 22;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter) {
			report(level, shooter, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.2F, 1.4F);

			for (int pellet = 0; pellet < PELLETS; pellet++) {
				Vec3d aim = Ballistics.spread(level, shooter.getRotationVec(1.0F), 0.22D);

				for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(), aim,
						20.0D, 0.4D, 0.6D, 1, ParticleTypes.CRIT)) {
					Ballistics.hurt(level, shooter, hit, 3.5F);
				}
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.shotgun");
		}
	}

	/** Six heavy, accurate shots. The sensible sidearm. */
	public static class Revolver extends SingleShot {
		public Revolver(Settings settings) {
			super(settings);
		}

		@Override
		protected int cooldown() {
			return 12;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter) {
			report(level, shooter, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.9F, 1.6F);

			for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(),
					shooter.getRotationVec(1.0F), 48.0D, 0.35D, 0.65D, 1, ParticleTypes.CRIT)) {
				Ballistics.hurt(level, shooter, hit, 9.0F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.revolver");
		}
	}

	/** One shot, a very long way, through everyone unlucky enough to be in a line. */
	public static class Sniper extends SingleShot {
		public Sniper(Settings settings) {
			super(settings);
		}

		@Override
		protected int cooldown() {
			return 45;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter) {
			ParticleEffect trail = new DustParticleEffect(new Vector3f(1.0F, 1.0F, 0.9F), 0.6F);
			report(level, shooter, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4F, 1.6F);

			for (LivingEntity hit : Ballistics.fire(level, shooter, shooter.getEyePos(),
					shooter.getRotationVec(1.0F), 160.0D, 0.3D, 0.65D, 5, trail)) {
				Ballistics.hurt(level, shooter, hit, 18.0F);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.sniper");
		}
	}

	/** Lobs a shell that goes off where it lands. Loud, and entirely for show. */
	public static class GrenadeLauncher extends SingleShot {
		public GrenadeLauncher(Settings settings) {
			super(settings);
		}

		@Override
		protected int cooldown() {
			return 40;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter) {
			SnowballEntity shell = new SnowballEntity(level, shooter);
			shell.setItem(new ItemStack(Items.TNT));
			shell.setVelocity(shooter, shooter.getPitch(), shooter.getYaw(), 0.0F, 1.2F, 1.0F);
			level.spawnEntity(shell);

			RpgManager.track(shell, shooter, RpgManager.Payload.GRENADE);

			report(level, shooter, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4F, 0.8F);
			level.spawnParticles(ParticleTypes.SMOKE,
					shooter.getX(), shooter.getEyeY() - 0.2D, shooter.getZ(), 15, 0.2D, 0.2D, 0.2D, 0.03D);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.grenade_launcher");
		}
	}

	// ------------------------------------------------------------------ the real one

	/**
	 * The one weapon here that actually damages the world.
	 *
	 * <p>Its blast is small on purpose - a bite out of a wall, not a crater - and it refuses to
	 * touch anything that might be holding items. See {@link com.slickfun.util.RealDemolition}
	 * for exactly what it will and will not break.
	 */
	public static class RedstoneRpg extends SingleShot {
		public RedstoneRpg(Settings settings) {
			super(settings);
		}

		@Override
		protected int cooldown() {
			return 20 * 4;
		}

		@Override
		protected void shoot(ServerWorld level, ServerPlayerEntity shooter) {
			SnowballEntity round = new SnowballEntity(level, shooter);
			round.setItem(new ItemStack(Items.REDSTONE_BLOCK));
			round.setVelocity(shooter, shooter.getPitch(), shooter.getYaw(), 0.0F, 1.6F, 0.4F);
			level.spawnEntity(round);

			RpgManager.track(round, shooter, RpgManager.Payload.REAL);

			report(level, shooter, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0F, 0.4F);
			level.spawnParticles(ParticleTypes.LARGE_SMOKE,
					shooter.getX(), shooter.getEyeY() - 0.2D, shooter.getZ(), 30, 0.3D, 0.3D, 0.3D, 0.05D);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.redstone_rpg").formatted(Formatting.RED));
			tooltip.add(Text.translatable("tooltip.slickfun.redstone_rpg.2").formatted(Formatting.DARK_GRAY));
		}
	}
}
