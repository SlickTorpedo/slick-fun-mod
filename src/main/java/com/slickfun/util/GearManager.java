package com.slickfun.util;

import java.util.List;
import java.util.Map;

import com.slickfun.item.FinalTools;
import com.slickfun.item.GagItems;
import com.slickfun.item.ScubaTankItem;
import com.slickfun.registry.ModItems;
import com.slickfun.screen.QuiverScreenHandler;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.inventory.SimpleInventory;
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

/**
 * The carried gear from the last batch: the quiver, the totem belt, the arrow charm and the
 * head hunter's charm.
 *
 * <p>Like {@link CharmManager} this makes one pass over each player's inventory per cycle
 * rather than letting each item tick itself.
 */
public final class GearManager {
	private static final int INTERVAL_TICKS = 10;

	/** How close the arrow charm reaches. Roughly a bow's useful range. */
	private static final double ARROW_RANGE = 24.0D;

	private static final float HEAD_CHANCE = 0.25F;

	/** Only mobs that actually have a head item in the game. */
	private static final Map<EntityType<?>, Item> HEADS = Map.of(
			EntityType.ZOMBIE, Items.ZOMBIE_HEAD,
			EntityType.SKELETON, Items.SKELETON_SKULL,
			EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL,
			EntityType.CREEPER, Items.CREEPER_HEAD,
			EntityType.PIGLIN, Items.PIGLIN_HEAD,
			EntityType.PIGLIN_BRUTE, Items.PIGLIN_HEAD,
			EntityType.ZOMBIFIED_PIGLIN, Items.PIGLIN_HEAD,
			EntityType.ENDER_DRAGON, Items.DRAGON_HEAD);

	private static int tickCounter;

	private GearManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GearManager::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register(GearManager::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(GearManager::afterDeath);
	}

	// ------------------------------------------------------------------ totem belt

	/**
	 * Vanilla checks your hands for a totem long before this point, inside the damage code, so
	 * a totem you are holding is spent the normal way and never reaches here. This only covers
	 * the case vanilla gives up on: a totem sitting in your pack.
	 */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity player) || !carrying(player, ModItems.TOTEM_BELT)) {
			return true;
		}

		// The totem itself may also be in a trinket slot, so search the same combined view.
		for (ItemStack stack : Carried.stacks(player)) {
			if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
				continue;
			}

			stack.decrement(1);
			revive(player);
			return false;
		}

		return true;
	}

	private static void revive(ServerPlayerEntity player) {
		player.setHealth(1.0F);
		player.clearStatusEffects();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
		player.getServerWorld().sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING);
		player.sendMessage(Text.translatable("message.slickfun.belt.saved").formatted(Formatting.GOLD), true);
	}

	// ------------------------------------------------------------------ head hunter

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.getWorld() instanceof ServerWorld world)
				|| !(source.getAttacker() instanceof ServerPlayerEntity killer)
				|| !carrying(killer, ModItems.HEAD_HUNTERS_CHARM)) {
			return;
		}

		Item head = HEADS.get(entity.getType());

		if (head == null || world.getRandom().nextFloat() >= HEAD_CHANCE) {
			return;
		}

		entity.dropStack(new ItemStack(head));
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.5F);
		killer.sendMessage(Text.translatable("message.slickfun.headhunter.dropped", entity.getName())
				.formatted(Formatting.LIGHT_PURPLE), true);
	}

	// ------------------------------------------------------------------ per-tick gear

	private static void tick(MinecraftServer server) {
		if (++tickCounter % INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}

			ItemStack quiver = ItemStack.EMPTY;
			ItemStack tank = ItemStack.EMPTY;
			boolean arrowCharm = false;
			boolean looseArrows = false;
			boolean headphones = false;

			// Inventory and trinket slots together - a worn charm must work like a carried one.
			for (ItemStack stack : Carried.stacks(player)) {
				if (stack.getItem() instanceof FinalTools.Quiver) {
					quiver = stack;
				} else if (stack.getItem() instanceof ScubaTankItem && ScubaTankItem.airLeft(stack) > 0) {
					// The fullest tank goes first, so a spare is not half-spent alongside it.
					if (tank.isEmpty() || ScubaTankItem.airLeft(stack) > ScubaTankItem.airLeft(tank)) {
						tank = stack;
					}
				} else if (stack.isOf(ModItems.ARROW_RECOVERY_CHARM)) {
					arrowCharm = true;
				} else if (stack.getItem() instanceof GagItems.NoiseCancellingHeadphones) {
					headphones = true;
				} else if (QuiverScreenHandler.isArrow(stack)) {
					looseArrows = true;
				}
			}

			if (!tank.isEmpty()) {
				breathe(player, tank);
			}

			if (headphones) {
				GagItems.NoiseCancellingHeadphones.amplify(player);
			}

			// The suit has to be actually worn, not just carried, or the joke does not land.
			if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof GagItems.BubbleSuit) {
				GagItems.BubbleSuit.wobble(player);
			}

			if (!quiver.isEmpty() && !looseArrows) {
				refill(player, quiver);
			}

			if (arrowCharm) {
				recoverArrows(player);
			}
		}
	}

	/**
	 * Spends tank air to keep a submerged player breathing.
	 *
	 * <p>It bills for the whole cycle it just covered rather than one tick, so the tank drains
	 * at real time regardless of how often this runs. The tank is never emptied below zero -
	 * once it is out, vanilla drowning takes over exactly as normal.
	 */
	private static void breathe(ServerPlayerEntity player, ItemStack tank) {
		if (!player.isSubmergedInWater() || player.isCreative()) {
			return;
		}

		int spend = Math.min(INTERVAL_TICKS, ScubaTankItem.airLeft(tank));

		if (spend <= 0) {
			return;
		}

		tank.setDamage(tank.getDamage() + spend);
		player.setAir(player.getMaxAir());

		if (player.getRandom().nextInt(4) == 0) {
			player.getServerWorld().spawnParticles(ParticleTypes.BUBBLE,
					player.getX(), player.getEyeY(), player.getZ(), 3, 0.2D, 0.1D, 0.2D, 0.0D);
		}

		// A last warning while there is still time to surface.
		if (ScubaTankItem.airLeft(tank) == 0) {
			player.sendMessage(Text.translatable("message.slickfun.scuba.empty").formatted(Formatting.RED), true);
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.6F, 0.5F);
		}
	}

	/** Pushes one stack out of the quiver so the bow always finds something to fire. */
	private static void refill(ServerPlayerEntity player, ItemStack quiver) {
		SimpleInventory stored = QuiverScreenHandler.read(quiver);

		for (int slot = 0; slot < stored.size(); slot++) {
			ItemStack arrows = stored.getStack(slot);

			if (arrows.isEmpty()) {
				continue;
			}

			if (player.getInventory().insertStack(arrows.copy())) {
				stored.setStack(slot, ItemStack.EMPTY);
				QuiverScreenHandler.write(quiver, stored);
				player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, 1.8F);
			}

			return;
		}
	}

	/**
	 * Hands landed arrows back to whoever shot them.
	 *
	 * <p>The eligibility decision is vanilla's, not ours: {@code onPlayerCollision} is exactly
	 * what runs when you walk over an arrow, and it already refuses anything still in flight,
	 * anything that has only just landed, and anything you are not allowed to pick up - an
	 * Infinity bow's arrows among them. It also returns the real item, so a tipped or spectral
	 * arrow comes back as itself rather than as a plain one.
	 *
	 * <p>An earlier version tried to spot landed arrows by looking for a velocity near zero.
	 * That never matched: an arrow that sticks in a block keeps its last velocity forever,
	 * because its tick returns early once it is in the ground and nothing ever clears it.
	 */
	private static void recoverArrows(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Box area = player.getBoundingBox().expand(ARROW_RANGE);
		List<PersistentProjectileEntity> arrows = world.getEntitiesByClass(PersistentProjectileEntity.class, area,
				arrow -> arrow.isAlive() && shotBy(arrow, player));

		for (PersistentProjectileEntity arrow : arrows) {
			arrow.onPlayerCollision(player);
		}
	}

	private static boolean shotBy(PersistentProjectileEntity arrow, ServerPlayerEntity player) {
		Entity owner = arrow.getOwner();
		return owner != null && owner.getUuid().equals(player.getUuid());
	}

	private static boolean carrying(ServerPlayerEntity player, Item item) {
		return Carried.has(player, item);
	}
}
