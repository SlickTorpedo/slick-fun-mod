package com.slickfun.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.slickfun.item.SoulTieItem;
import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModDamageTypes;
import com.slickfun.registry.ModItems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Runs the Soul Tie's three rituals: tying, travelling and severing.
 *
 * <p>Offers and sever attempts are deliberately kept here in memory rather than on the item.
 * Both are meant to be fleeting - an offer nobody answered should not sit on a tie forever,
 * and a half-finished severing must not survive a relog and catch someone out later.
 */
public final class SoulTieManager {
	private static final int OFFER_TICKS = 20 * 30;
	private static final int SEVER_WINDOW_TICKS = 20 * 5;

	private record Offer(UUID target, long expiresAt) {
	}

	private record Sever(UUID partner, long expiresAt) {
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	private static final Map<UUID, Sever> SEVERS = new HashMap<>();

	private static long tickCounter;

	private SoulTieManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SoulTieManager::tick);

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient || !player.isSneaking()) {
				return ActionResult.PASS;
			}

			ItemStack held = player.getStackInHand(hand);

			if (!(held.getItem() instanceof SoulTieItem)
					|| !(player instanceof ServerPlayerEntity breaker)
					|| !(entity instanceof ServerPlayerEntity partner)) {
				return ActionResult.PASS;
			}

			if (!SoulTieItem.bondOf(held).isBoundTo(partner.getUuid())) {
				return ActionResult.PASS;
			}

			sever(breaker, partner, held, hand);
			// Swallowing the hit matters: the ritual must not double as a free melee swing.
			return ActionResult.SUCCESS;
		});
	}

	// ------------------------------------------------------------------ tying

	public static void offer(ServerPlayerEntity player, ServerPlayerEntity target, ItemStack stack) {
		if (player == target) {
			return;
		}

		SoulBond bond = SoulTieItem.bondOf(stack);

		if (bond.isBound()) {
			player.sendMessage(Text.translatable("message.slickfun.soul.already", bond.partnerName())
					.formatted(Formatting.GRAY), true);
			return;
		}

		Offer theirs = OFFERS.get(target.getUuid());

		if (theirs != null && theirs.target().equals(player.getUuid()) && theirs.expiresAt() > tickCounter) {
			ItemStack theirTie = findUnbound(target);

			if (theirTie == null) {
				player.sendMessage(Text.translatable("message.slickfun.soul.they_lost_it", target.getName())
						.formatted(Formatting.GRAY), true);
				return;
			}

			OFFERS.remove(target.getUuid());
			OFFERS.remove(player.getUuid());
			bind(player, stack, target, theirTie);
			return;
		}

		OFFERS.put(player.getUuid(), new Offer(target.getUuid(), tickCounter + OFFER_TICKS));
		player.sendMessage(Text.translatable("message.slickfun.soul.offered", target.getName())
				.formatted(Formatting.LIGHT_PURPLE), false);
		target.sendMessage(Text.translatable("message.slickfun.soul.asked", player.getName())
				.formatted(Formatting.LIGHT_PURPLE), false);
	}

	private static ItemStack findUnbound(ServerPlayerEntity player) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);

			if (stack.getItem() instanceof SoulTieItem && !SoulTieItem.bondOf(stack).isBound()) {
				return stack;
			}
		}

		return null;
	}

	private static void bind(ServerPlayerEntity a, ItemStack aTie, ServerPlayerEntity b, ItemStack bTie) {
		aTie.set(ModComponents.SOUL_BOND,
				SoulTieItem.bondOf(aTie).tiedTo(b.getUuid(), b.getGameProfile().getName()));
		bTie.set(ModComponents.SOUL_BOND,
				SoulTieItem.bondOf(bTie).tiedTo(a.getUuid(), a.getGameProfile().getName()));

		for (ServerPlayerEntity player : new ServerPlayerEntity[] {a, b}) {
			ServerWorld world = player.getServerWorld();
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 1.4F);
			world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
					40, 0.4D, 0.8D, 0.4D, 0.05D);
		}

		a.sendMessage(Text.translatable("message.slickfun.soul.tied", b.getName()).formatted(Formatting.LIGHT_PURPLE), false);
		b.sendMessage(Text.translatable("message.slickfun.soul.tied", a.getName()).formatted(Formatting.LIGHT_PURPLE), false);
	}

	// ------------------------------------------------------------------ travelling

	public static boolean travel(ServerPlayerEntity player, ItemStack stack) {
		SoulBond bond = SoulTieItem.bondOf(stack);

		if (!bond.isBound()) {
			player.sendMessage(Text.translatable("message.slickfun.soul.untied").formatted(Formatting.GRAY), true);
			return false;
		}

		MinecraftServer server = player.getServer();
		ServerPlayerEntity partner = server == null
				? null
				: server.getPlayerManager().getPlayer(bond.partner().orElseThrow());

		if (partner == null) {
			player.sendMessage(Text.translatable("message.slickfun.soul.offline", bond.partnerName())
					.formatted(Formatting.GRAY), true);
			return false;
		}

		ServerWorld destination = partner.getServerWorld();
		RegistryKey<World> dimension = destination.getRegistryKey();

		if (!bond.reaches(dimension)) {
			player.sendMessage(Text.translatable("message.slickfun.soul.too_far",
					bond.partnerName(), needed(dimension)).formatted(Formatting.RED), false);
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8F, 0.8F);
			return false;
		}

		Vec3d from = player.getPos();
		ServerWorld origin = player.getServerWorld();

		origin.spawnParticles(ParticleTypes.SOUL, from.x, from.y + 1.0D, from.z, 40, 0.4D, 0.8D, 0.4D, 0.1D);
		origin.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.6F);

		player.teleport(destination, partner.getX(), partner.getY(), partner.getZ(), player.getYaw(), player.getPitch());
		player.fallDistance = 0.0F;

		destination.spawnParticles(ParticleTypes.SOUL, partner.getX(), partner.getY() + 1.0D, partner.getZ(),
				40, 0.4D, 0.8D, 0.4D, 0.1D);
		destination.playSound(null, partner.getX(), partner.getY(), partner.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.6F);

		partner.sendMessage(Text.translatable("message.slickfun.soul.arrived", player.getName())
				.formatted(Formatting.LIGHT_PURPLE), true);

		return true;
	}

	private static Text needed(RegistryKey<World> dimension) {
		return dimension.equals(World.NETHER)
				? Text.translatable("message.slickfun.soul.need_netherrack", SoulBond.NETHERRACK_COST)
				: Text.translatable("message.slickfun.soul.need_pearls", SoulBond.PEARL_COST);
	}

	// ------------------------------------------------------------------ severing

	private static void sever(ServerPlayerEntity breaker, ServerPlayerEntity partner, ItemStack tie,
			net.minecraft.util.Hand hand) {
		Sever pending = SEVERS.get(breaker.getUuid());

		if (pending == null || !pending.partner().equals(partner.getUuid()) || pending.expiresAt() <= tickCounter) {
			SEVERS.put(breaker.getUuid(), new Sever(partner.getUuid(), tickCounter + SEVER_WINDOW_TICKS));
			breaker.sendMessage(Text.translatable("message.slickfun.soul.sever_warn",
					partner.getName(), SEVER_WINDOW_TICKS / 20).formatted(Formatting.DARK_RED, Formatting.BOLD), false);
			breaker.getServerWorld().playSound(null, breaker.getX(), breaker.getY(), breaker.getZ(),
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.5F);
			return;
		}

		SEVERS.remove(breaker.getUuid());
		SEVERS.remove(partner.getUuid());

		breaker.setStackInHand(hand, broken(tie));
		breakTheirs(partner, breaker.getUuid());

		for (ServerPlayerEntity player : new ServerPlayerEntity[] {breaker, partner}) {
			ServerWorld world = player.getServerWorld();
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6F, 1.6F);
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0D, player.getZ(),
					60, 0.5D, 1.0D, 0.5D, 0.15D);
		}

		partner.sendMessage(Text.translatable("message.slickfun.soul.severed_by", breaker.getName())
				.formatted(Formatting.DARK_RED), false);

		MinecraftServer server = breaker.getServer();

		if (server != null) {
			server.getPlayerManager().broadcast(Text.translatable("message.slickfun.soul.severed_broadcast",
					breaker.getName(), partner.getName()).formatted(Formatting.DARK_PURPLE), false);
		}

		// Cutting the tie is what kills you - it takes the soul with it.
		breaker.damage(ModDamageTypes.source(breaker.getWorld(), ModDamageTypes.SOUL_SEVERED), Float.MAX_VALUE);
	}

	/** Turns their half of the tie into a keepsake too, wherever it is in their pack. */
	private static void breakTheirs(ServerPlayerEntity partner, UUID breaker) {
		for (int slot = 0; slot < partner.getInventory().size(); slot++) {
			ItemStack stack = partner.getInventory().getStack(slot);

			if (stack.getItem() instanceof SoulTieItem && SoulTieItem.bondOf(stack).isBoundTo(breaker)) {
				partner.getInventory().setStack(slot, broken(stack));
				return;
			}
		}
	}

	/** Keeps the bond data so the keepsake can still name who it was. */
	private static ItemStack broken(ItemStack tie) {
		ItemStack keepsake = new ItemStack(ModItems.BROKEN_SOUL_TIE);
		keepsake.set(ModComponents.SOUL_BOND, SoulTieItem.bondOf(tie));
		return keepsake;
	}

	// ------------------------------------------------------------------ housekeeping

	private static void tick(MinecraftServer server) {
		tickCounter++;

		if (tickCounter % 20 != 0) {
			return;
		}

		OFFERS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= tickCounter);
		SEVERS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= tickCounter);
	}
}
