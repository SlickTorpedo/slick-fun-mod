package com.slickfun.item;

import com.slickfun.util.ServerScheduler;
import com.slickfun.util.ToiletFortune;
import com.slickfun.util.ToolHost;

import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Does nothing. Flushes loudly. Occasionally, very occasionally, produces something.
 *
 * <p>There is no vanilla flush sound, so one is assembled from a bucket empty, a splash, a
 * whirlpool and a refill, staged a few ticks apart.
 */
public class PortableToiletItem extends PortableUtilityItem {
	private static final int COOLDOWN_TICKS = 40;
	private static final float FART_CHANCE = 0.05F;

	public PortableToiletItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "portable_toilet";
	}

	@Override
	public void openFor(ServerPlayerEntity player, ToolHost host) {
		if (player.getItemCooldownManager().isCoolingDown(this)) {
			return;
		}

		player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		player.sendMessage(Text.translatable("message.slickfun.toilet").formatted(Formatting.WHITE), true);

		ServerWorld world = player.getServerWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();

		play(world, x, y, z, SoundEvents.ITEM_BUCKET_EMPTY, 1.0F, 0.7F);

		ServerScheduler.schedule(3, () -> {
			play(world, x, y, z, SoundEvents.BLOCK_WATER_AMBIENT, 1.2F, 0.8F);
			world.spawnParticles(ParticleTypes.SPLASH, x, y + 0.3D, z, 25, 0.3D, 0.2D, 0.3D, 0.1D);
		});

		ServerScheduler.schedule(7, () -> {
			play(world, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, 0.9F, 0.6F);
			world.spawnParticles(ParticleTypes.BUBBLE, x, y + 0.2D, z, 30, 0.3D, 0.2D, 0.3D, 0.05D);
		});

		ServerScheduler.schedule(14, () ->
				play(world, x, y, z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.0F, 0.9F));

		ServerScheduler.schedule(24, () ->
				play(world, x, y, z, SoundEvents.ITEM_BUCKET_FILL, 0.8F, 1.3F));

		// Positional, so everyone standing nearby is included in the moment.
		if (world.getRandom().nextFloat() < FART_CHANCE) {
			ServerScheduler.schedule(10, () -> {
				play(world, x, y, z, SoundEvents.ENTITY_PLAYER_BURP, 1.0F, 0.35F);
				play(world, x, y, z, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 1.0F, 0.5F);
				world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.2D, z, 6, 0.2D, 0.1D, 0.2D, 0.01D);
			});
		}

		rollFortune(player, world, x, y, z);
	}

	private void rollFortune(ServerPlayerEntity player, ServerWorld world, double x, double y, double z) {
		if (!ToiletFortune.hasWandered(player)) {
			return;
		}

		ToiletFortune.markRolled(player);
		ToiletFortune.Result result = ToiletFortune.roll(world.getRandom());

		if (result.tier() == ToiletFortune.Tier.NOTHING) {
			return;
		}

		ServerScheduler.schedule(20, () -> {
			if (player.isRemoved()) {
				return;
			}

			ItemStack prize = result.prize();

			if (!player.getInventory().insertStack(prize)) {
				player.dropItem(prize, false);
			}

			switch (result.tier()) {
				case GOOD -> {
					announce(player, "message.slickfun.toilet.good", Formatting.GREEN, false);
					play(world, x, y, z, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
				}
				case AMAZING -> {
					announce(player, "message.slickfun.toilet.amazing", Formatting.GOLD, true);
					play(world, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
				}
				case DIAMOND -> {
					announce(player, "message.slickfun.toilet.diamond", Formatting.AQUA, true);
					play(world, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.7F);
					play(world, x, y, z, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
				}
				default -> {
				}
			}

			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6D, z, 30, 0.4D, 0.5D, 0.4D, 0.1D);
		});
	}

	private static void announce(ServerPlayerEntity player, String key, Formatting colour, boolean serverWide) {
		Text message = Text.translatable(key, player.getDisplayName()).formatted(colour);

		if (serverWide && player.getServer() != null) {
			player.getServer().getPlayerManager().broadcast(message, false);
		} else {
			player.sendMessage(message, false);
		}
	}

	private static void play(ServerWorld world, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, x, y, z, sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
