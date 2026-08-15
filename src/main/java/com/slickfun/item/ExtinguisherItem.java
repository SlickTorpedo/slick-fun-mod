package com.slickfun.item;

import java.util.List;

import com.slickfun.util.FakeDemolition;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Three extinguishers that look identical in the hand and do very different things.
 *
 * <p>All of them share one control scheme: right click points it at yourself, left click
 * points it at someone else. That is the whole reason the evil ones work - by the time anyone
 * can tell which is which, they have already used it.
 */
public class ExtinguisherItem extends Item {
	public enum Mode {
		/** Does what it says. */
		HELPFUL(10),
		/** Does the exact opposite. */
		EVIL(30),
		/** Makes it look like the world caught. */
		SUPER_EVIL(20 * 20);

		private final int cooldown;

		Mode(int cooldown) {
			this.cooldown = cooldown;
		}
	}

	/** How much of the world appears to catch fire. */
	private static final int INFERNO_RADIUS = 26;
	private static final int INFERNO_MAX_BLOCKS = 30000;
	private static final int INFERNO_TICKS = 20 * 10;
	private static final int VIEWER_RANGE = 96;

	private final Mode mode;

	public ExtinguisherItem(Settings settings, Mode mode) {
		super(settings);
		this.mode = mode;
	}

	/** How far the nozzle reaches when you left click. */
	private static final double SPRAY_RANGE = 24.0D;

	/**
	 * Left click aims it at whoever you are looking at, instead of swinging.
	 *
	 * <p>Two hooks are needed. A left click that lands on an entity in melee reach arrives as
	 * an attack, which has to be swallowed or you punch them as well; a left click at anything
	 * further away arrives only as a swing, which is what the mixin catches. Whichever comes
	 * first does the work, and the cooldown stops the other one repeating it.
	 */
	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient) {
				return ActionResult.PASS;
			}

			if (!(player.getStackInHand(hand).getItem() instanceof ExtinguisherItem extinguisher)
					|| !(player instanceof ServerPlayerEntity user)) {
				return ActionResult.PASS;
			}

			extinguisher.spray(user, entity);
			// Swallowed, so it sprays rather than also punching them.
			return ActionResult.SUCCESS;
		});
	}

	/** Called from the swing mixin for every left click, hit or miss. */
	public static void onSwing(ServerPlayerEntity player, Hand hand) {
		if (!(player.getStackInHand(hand).getItem() instanceof ExtinguisherItem extinguisher)) {
			return;
		}

		LivingEntity target = extinguisher.lookingAt(player);

		// No target means an ordinary swing - mining, or missing - so nothing happens and no
		// cooldown is burnt. Only an actual aim at someone triggers it.
		if (target != null) {
			extinguisher.spray(player, target);
		}
	}

	/** The first living thing on the line of sight, blocked by terrain. */
	private LivingEntity lookingAt(ServerPlayerEntity player) {
		ServerWorld level = player.getServerWorld();
		Vec3d eyes = player.getEyePos();
		Vec3d aim = player.getRotationVec(1.0F);

		for (double travelled = 0.5D; travelled < SPRAY_RANGE; travelled += 0.35D) {
			Vec3d point = eyes.add(aim.multiply(travelled));
			BlockPos block = BlockPos.ofFloored(point);

			if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
				return null;
			}

			Box around = new Box(point, point).expand(0.7D);

			for (LivingEntity candidate : level.getEntitiesByClass(LivingEntity.class, around,
					other -> other != player && other.isAlive() && !other.isSpectator())) {
				return candidate;
			}
		}

		return null;
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

		return spray(player, player) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
	}

	private boolean spray(ServerPlayerEntity user, Entity target) {
		if (user.getItemCooldownManager().isCoolingDown(this)) {
			return false;
		}

		user.getItemCooldownManager().set(this, this.mode.cooldown);

		ServerWorld level = user.getServerWorld();
		hiss(level, user);

		switch (this.mode) {
			case HELPFUL -> putOut(level, user, target);
			case EVIL -> ignite(level, user, target);
			case SUPER_EVIL -> burnTheWorld(level, user, target);
		}

		return true;
	}

	/** The convincing part: a hiss and a cloud of white, identical on all three. */
	private static void hiss(ServerWorld level, ServerPlayerEntity user) {
		Vec3d nozzle = user.getPos().add(user.getRotationVec(1.0F).multiply(1.2D)).add(0.0D, 1.2D, 0.0D);

		level.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.2F, 0.8F);
		level.spawnParticles(ParticleTypes.CLOUD, nozzle.x, nozzle.y, nozzle.z, 60, 0.4D, 0.4D, 0.4D, 0.08D);
	}

	private static void putOut(ServerWorld level, ServerPlayerEntity user, Entity target) {
		target.extinguish();
		target.setFireTicks(0);

		level.spawnParticles(ParticleTypes.SPLASH,
				target.getX(), target.getBodyY(0.6D), target.getZ(), 30, 0.4D, 0.6D, 0.4D, 0.05D);

		if (target == user) {
			user.sendMessage(Text.translatable("message.slickfun.extinguisher.self").formatted(Formatting.AQUA), true);
			return;
		}

		user.sendMessage(Text.translatable("message.slickfun.extinguisher.other", target.getName())
				.formatted(Formatting.AQUA), true);

		if (target instanceof ServerPlayerEntity saved) {
			saved.sendMessage(Text.translatable("message.slickfun.extinguisher.saved", user.getName())
					.formatted(Formatting.AQUA), true);
		}
	}

	private static void ignite(ServerWorld level, ServerPlayerEntity user, Entity target) {
		target.setOnFireFor(6);

		level.spawnParticles(ParticleTypes.FLAME,
				target.getX(), target.getBodyY(0.6D), target.getZ(), 40, 0.4D, 0.6D, 0.4D, 0.05D);

		if (target == user) {
			user.sendMessage(Text.translatable("message.slickfun.extinguisher.backfire").formatted(Formatting.RED), true);
			return;
		}

		if (target instanceof ServerPlayerEntity victim) {
			victim.sendMessage(Text.translatable("message.slickfun.extinguisher.lit", user.getName())
					.formatted(Formatting.RED), true);
		}
	}

	/**
	 * Sets fire to nothing at all, very convincingly.
	 *
	 * <p>Shown to everyone in range, not just the victim, so the whole server watches the same
	 * fire and agrees it is happening. Nothing is actually alight anywhere.
	 */
	private static void burnTheWorld(ServerWorld level, ServerPlayerEntity user, Entity target) {
		ServerPlayerEntity victim = target instanceof ServerPlayerEntity player ? player : user;
		var inferno = FakeDemolition.inferno(level, victim.getBlockPos(), INFERNO_RADIUS, INFERNO_MAX_BLOCKS);

		if (inferno.isEmpty()) {
			return;
		}

		List<ServerPlayerEntity> viewers = FakeDemolition.viewersWithin(level, victim.getBlockPos(), VIEWER_RANGE);
		FakeDemolition.show(level, viewers, inferno);

		level.playSound(null, victim.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 3.0F, 0.6F);
		level.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.PLAYERS, 3.0F, 0.7F);
		level.spawnParticles(victim, ParticleTypes.FLAME, true,
				victim.getX(), victim.getEyeY(), victim.getZ(), 120, 6.0D, 3.0D, 6.0D, 0.05D);

		victim.sendMessage(Text.translatable("message.slickfun.extinguisher.inferno").formatted(Formatting.GOLD), false);

		com.slickfun.util.ServerScheduler.schedule(INFERNO_TICKS,
				() -> FakeDemolition.restore(level, viewers, inferno.keySet()));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.extinguisher").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.extinguisher.controls").formatted(Formatting.DARK_GRAY));
	}
}
