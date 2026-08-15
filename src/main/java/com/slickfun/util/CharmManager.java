package com.slickfun.util;

import java.util.List;

import com.slickfun.item.CharmTools;
import com.slickfun.item.TravelTools;
import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Drives the charms that work simply by being carried.
 *
 * <p>All of them share one scan of the player's inventory per cycle rather than each item
 * ticking itself, which keeps the cost to a single pass no matter how many charms exist.
 */
public final class CharmManager {
	private static final int INTERVAL_TICKS = 10;

	private static int tickCounter;

	private CharmManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CharmManager::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CharmManager::allowDamage);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity player)) {
			return true;
		}

		// Slime Boots: fall damage never lands, and you bounce instead.
		if (source.isOf(DamageTypes.FALL) && carrying(player, ModItems.SLIME_BOOTS)) {
			bounce(player, amount);
			return false;
		}

		// Shield Charger: one free block, then the charge is spent.
		ItemStack shield = player.getOffHandStack();

		if (shield.isOf(Items.SHIELD) && shield.getOrDefault(ModComponents.SHIELD_CHARGED, false)
				&& !source.isOf(DamageTypes.STARVE) && !source.isOf(DamageTypes.OUT_OF_WORLD)) {
			shield.set(ModComponents.SHIELD_CHARGED, false);
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 0.6F);
			player.sendMessage(Text.translatable("message.slickfun.charger.spent").formatted(Formatting.AQUA), true);
			return false;
		}

		return true;
	}

	private static void bounce(ServerPlayerEntity player, float fallDamage) {
		ServerWorld world = player.getServerWorld();
		double strength = Math.min(0.9D, 0.25D + fallDamage * 0.05D);

		player.setVelocity(player.getVelocity().x, strength, player.getVelocity().z);
		player.velocityModified = true;
		player.fallDistance = 0.0F;

		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.PLAYERS, 1.0F, 1.0F);
		world.spawnParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY(), player.getZ(),
				12, 0.3D, 0.1D, 0.3D, 0.05D);
	}

	private static void tick(MinecraftServer server) {
		if (++tickCounter % INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}

			boolean skates = false;
			boolean fisher = false;
			ItemStack repellent = ItemStack.EMPTY;
			ItemStack filter = ItemStack.EMPTY;

			// Inventory and trinket slots together - wearing a charm must work like carrying it.
			for (ItemStack stack : Carried.stacks(player)) {
				if (stack.isOf(ModItems.SPEED_SKATES)) {
					skates = true;
				} else if (stack.isOf(ModItems.FISHERS_CHARM)) {
					fisher = true;
				} else if (stack.getItem() instanceof CharmTools.MobRepellent && CharmTools.MobRepellent.isActive(stack)) {
					repellent = stack;
				} else if (stack.getItem() instanceof CharmTools.VoidFilter) {
					filter = stack;
				}
			}

			if (skates) {
				applySkates(player);
			}

			if (fisher) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 40, 1, false, false, false));
			}

			if (!repellent.isEmpty()) {
				repel(player);
			}

			if (!filter.isEmpty()) {
				voidJunk(player, filter);
			}
		}
	}

	private static void applySkates(ServerPlayerEntity player) {
		if (TravelTools.SpeedSkates.isIce(player.getWorld(), player.getBlockPos().down())) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, false, false, true));
		}
	}

	private static void repel(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Box area = player.getBoundingBox().expand(CharmTools.MobRepellent.RADIUS);
		List<HostileEntity> nearby = world.getEntitiesByClass(HostileEntity.class, area, LivingEntity::isAlive);

		for (HostileEntity mob : nearby) {
			Vec3d away = mob.getPos().subtract(player.getPos());

			if (away.horizontalLengthSquared() < 1.0E-4D) {
				continue;
			}

			Vec3d push = away.multiply(1.0D, 0.0D, 1.0D).normalize().multiply(0.45D);
			mob.setVelocity(push.x, 0.2D, push.z);
			mob.velocityModified = true;
		}
	}

	private static void voidJunk(ServerPlayerEntity player, ItemStack filter) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (stack.isEmpty() || stack == filter) {
				continue;
			}

			if (CharmTools.VoidFilter.filters(filter, stack)) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
			}
		}
	}

	private static boolean carrying(ServerPlayerEntity player, Item item) {
		return Carried.has(player, item);
	}
}
