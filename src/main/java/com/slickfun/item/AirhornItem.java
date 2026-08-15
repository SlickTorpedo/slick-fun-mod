package com.slickfun.item;

import java.util.List;

import com.slickfun.util.ServerScheduler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
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
import net.minecraft.world.World;

/**
 * Three grades of obnoxious.
 *
 * <p>A single sound cannot be made louder than its own recording - past volume 1.0 the number
 * only widens the radius. Loudness therefore comes from <em>stacking</em>: simultaneous
 * sounds mix additively, so the same horn played eight times in one tick at slightly
 * different pitches is genuinely eight times the signal. Each tier just stacks harder.
 */
public class AirhornItem extends Item {
	public enum Tier {
		NORMAL("airhorn", 24.0F, 20 * 30),
		LOUD("loud_airhorn", 40.0F, 20 * 45),
		SUPER("super_airhorn", 64.0F, 20 * 90);

		private final String key;
		private final float radius;
		private final int cooldown;

		Tier(String key, float radius, int cooldown) {
			this.key = key;
			this.radius = radius;
			this.cooldown = cooldown;
		}

		public String key() {
			return key;
		}
	}

	private final Tier tier;

	public AirhornItem(Settings settings, Tier tier) {
		super(settings);
		this.tier = tier;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		switch (this.tier) {
			case NORMAL -> normal(world, user);
			case LOUD -> loud(world, user);
			case SUPER -> superHorn(world, user);
		}

		user.getItemCooldownManager().set(this, this.tier.cooldown);
		return TypedActionResult.success(stack, false);
	}

	// ---------------------------------------------------------------- the three performances

	private void normal(World world, PlayerEntity user) {
		blast(world, user, 0, SoundEvents.EVENT_RAID_HORN.value(), 0.7F, 0.8F, 0.9F);
		blast(world, user, 0, SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 0.5F, 0.6F);
		blast(world, user, 0, SoundEvents.ENTITY_RAVAGER_ROAR, 0.9F);

		blast(world, user, 3, SoundEvents.EVENT_RAID_HORN.value(), 1.0F, 1.1F);
		blast(world, user, 3, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.7F);
		blast(world, user, 3, SoundEvents.BLOCK_BELL_USE, 1.6F, 1.9F);

		blast(world, user, 7, SoundEvents.EVENT_RAID_HORN.value(), 0.6F, 1.3F);
		blast(world, user, 7, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0F);
		blast(world, user, 7, SoundEvents.BLOCK_ANVIL_LAND, 1.4F);

		blast(world, user, 12, SoundEvents.ENTITY_WITHER_SPAWN, 1.0F);
		blast(world, user, 12, SoundEvents.EVENT_RAID_HORN.value(), 0.9F, 1.0F, 1.2F);
		blast(world, user, 12, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.5F);

		blast(world, user, 20, SoundEvents.EVENT_RAID_HORN.value(), 0.5F, 0.7F, 1.0F, 1.4F);
		blast(world, user, 20, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0F);

		puff(world, user, 40);
	}

	private void loud(World world, PlayerEntity user) {
		// Everything the normal one does, twice as thick and half again as long.
		for (int wave = 0; wave < 8; wave++) {
			int at = wave * 5;
			blast(world, user, at, SoundEvents.EVENT_RAID_HORN.value(), 0.5F, 0.7F, 0.9F, 1.1F, 1.3F);
			blast(world, user, at, SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 0.5F, 0.6F, 0.7F);
			blast(world, user, at + 2, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8F, 1.2F);
		}

		blast(world, user, 6, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.6F, 0.9F);
		blast(world, user, 14, SoundEvents.ENTITY_WITHER_SPAWN, 0.9F);
		blast(world, user, 22, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8F, 1.2F);
		blast(world, user, 30, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.0F);
		blast(world, user, 38, SoundEvents.BLOCK_ANVIL_LAND, 1.2F, 1.6F);

		puff(world, user, 80);
	}

	/**
	 * Deliberately theatrical: a rising whine, a silence, then roughly seventy simultaneous
	 * sounds, then four seconds of aftermath. Announced to the whole server, because at this
	 * point everyone is going to hear about it anyway.
	 */
	private void superHorn(World world, PlayerEntity user) {
		if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
			return;
		}

		// 1. The wind-up - a low note climbing for a second and a half.
		for (int step = 0; step < 12; step++) {
			blast(world, user, step * 2, SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 0.5F + step * 0.06F);
		}

		// 2. A beat of nothing. This is the part that sells it.
		// 3. The wall.
		int drop = 30;
		blast(world, user, drop, SoundEvents.EVENT_RAID_HORN.value(),
				0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 1.0F, 1.1F, 1.2F, 1.3F, 1.5F);
		blast(world, user, drop, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.5F, 0.7F, 1.0F);
		blast(world, user, drop, SoundEvents.ENTITY_WITHER_SPAWN, 0.7F, 1.0F);
		blast(world, user, drop, SoundEvents.ENTITY_WITHER_DEATH, 0.8F);
		blast(world, user, drop, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6F, 0.9F, 1.2F);
		blast(world, user, drop, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.0F, 1.3F);
		blast(world, user, drop, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.6F, 1.0F);
		blast(world, user, drop, SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.0F);
		blast(world, user, drop, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0F, 1.4F);
		blast(world, user, drop, SoundEvents.BLOCK_ANVIL_LAND, 0.8F, 1.2F, 1.6F);
		blast(world, user, drop, SoundEvents.BLOCK_BELL_USE, 1.2F, 1.6F, 2.0F);
		blast(world, user, drop, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.7F, 1.0F, 1.3F);

		// 4. Aftermath - five more waves so it does not just stop.
		for (int wave = 1; wave <= 5; wave++) {
			int at = drop + wave * 8;
			blast(world, user, at, SoundEvents.EVENT_RAID_HORN.value(), 0.6F, 0.8F, 1.0F, 1.2F, 1.4F);
			blast(world, user, at, SoundEvents.ENTITY_RAVAGER_ROAR, 0.7F, 1.1F);
			blast(world, user, at + 4, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9F);
		}

		ServerScheduler.schedule(drop, () -> {
			if (player.isRemoved()) {
				return;
			}

			serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
					player.getX(), player.getBodyY(1.0D), player.getZ(), 6, 1.5D, 1.0D, 1.5D, 0.0D);
			serverWorld.spawnParticles(ParticleTypes.NOTE,
					player.getX(), player.getBodyY(1.2D), player.getZ(), 200, 2.5D, 1.5D, 2.5D, 1.0D);
			serverWorld.spawnParticles(ParticleTypes.FIREWORK,
					player.getX(), player.getBodyY(1.5D), player.getZ(), 120, 2.0D, 2.0D, 2.0D, 0.4D);

			shout(serverWorld, player);
		});

		if (player.getServer() != null) {
			player.getServer().getPlayerManager().broadcast(
					Text.translatable("message.slickfun.airhorn.super", player.getDisplayName())
							.formatted(Formatting.RED, Formatting.BOLD), false);
		}
	}

	/** Puts it on screen for anyone close enough to have been assaulted by it. */
	private void shout(ServerWorld world, ServerPlayerEntity source) {
		for (ServerPlayerEntity nearby : world.getPlayers(
				p -> p.squaredDistanceTo(source) < this.tier.radius * this.tier.radius)) {
			nearby.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 25, 15));
			nearby.networkHandler.sendPacket(new TitleS2CPacket(
					Text.translatable("title.slickfun.airhorn").formatted(Formatting.RED, Formatting.BOLD)));
			nearby.networkHandler.sendPacket(new SubtitleS2CPacket(
					Text.translatable("title.slickfun.airhorn.sub").formatted(Formatting.GOLD)));
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Plays one sound once per pitch given, all in the same tick. */
	private void blast(World world, PlayerEntity user, int delay, SoundEvent sound, float... pitches) {
		double x = user.getX();
		double y = user.getY();
		double z = user.getZ();

		Runnable fire = () -> {
			for (float pitch : pitches) {
				world.playSound(null, x, y, z, sound, SoundCategory.PLAYERS, this.tier.radius, pitch);
			}
		};

		if (delay <= 0) {
			fire.run();
		} else {
			ServerScheduler.schedule(delay, fire);
		}
	}

	private static void puff(World world, PlayerEntity user, int count) {
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.NOTE,
					user.getX(), user.getBodyY(1.2D), user.getZ(), count, 0.8D, 0.6D, 0.8D, 1.0D);
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun." + this.tier.key() + ".1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun." + this.tier.key() + ".2").formatted(Formatting.DARK_GRAY));
	}
}
