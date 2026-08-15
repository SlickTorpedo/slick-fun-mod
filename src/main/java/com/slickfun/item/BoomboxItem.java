package com.slickfun.item;

import java.util.List;
import java.util.Optional;

import com.slickfun.registry.ModComponents;

import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.JukeboxPlayableComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A jukebox you carry. Hold a music disc in your off hand and right click to play it where
 * you're standing; sneak and right click to stop.
 *
 * <p>No disc slot on purpose - the off hand already is one, and it means the boombox never
 * eats anyone's Pigstep.
 */
public class BoomboxItem extends Item {
	private static final float VOLUME = 4.0F;
	private static final double EARSHOT = 64.0D;

	public BoomboxItem(Settings settings) {
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

		if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
			return TypedActionResult.fail(stack);
		}

		// Right click again to stop.
		if (stack.getOrDefault(ModComponents.BOOMBOX_PLAYING, false)) {
			stack.set(ModComponents.BOOMBOX_PLAYING, false);
			stopEverything(serverWorld, player);
			player.sendMessage(Text.translatable("message.slickfun.boombox.stopped").formatted(Formatting.GRAY), true);
			return TypedActionResult.success(stack, false);
		}

		JukeboxPlayableComponent playable = user.getOffHandStack().get(DataComponentTypes.JUKEBOX_PLAYABLE);

		if (playable == null) {
			player.sendMessage(Text.translatable("message.slickfun.boombox.no_disc").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		Optional<RegistryEntry<JukeboxSong>> song = playable.song().getEntry(serverWorld.getRegistryManager());

		if (song.isEmpty()) {
			return TypedActionResult.fail(stack);
		}

		JukeboxSong track = song.get().value();

		// One boombox at a time in any given room.
		stopEverything(serverWorld, player);

		// Attached to the player rather than to a fixed point, so the music travels with you
		// and falls off with distance for everyone else.
		serverWorld.playSoundFromEntity(null, player, track.soundEvent().value(), SoundCategory.RECORDS, VOLUME, 1.0F);
		stack.set(ModComponents.BOOMBOX_PLAYING, true);

		Text nowPlaying = Text.translatable("message.slickfun.boombox.playing",
				player.getDisplayName(), track.description()).formatted(Formatting.AQUA);

		// Only the people who can actually hear it get told about it.
		for (ServerPlayerEntity listener : nearby(serverWorld, player)) {
			listener.sendMessage(nowPlaying, true);
		}

		return TypedActionResult.success(stack, false);
	}

	/** A null sound id stops every sound in the category, which is exactly what we want. */
	private static void stopEverything(ServerWorld world, ServerPlayerEntity source) {
		StopSoundS2CPacket packet = new StopSoundS2CPacket(null, SoundCategory.RECORDS);

		for (ServerPlayerEntity listener : nearby(world, source)) {
			listener.networkHandler.sendPacket(packet);
		}
	}

	private static List<ServerPlayerEntity> nearby(ServerWorld world, ServerPlayerEntity source) {
		return world.getPlayers(player -> player.squaredDistanceTo(source) < EARSHOT * EARSHOT);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.boombox").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.boombox.2").formatted(Formatting.DARK_GRAY));
	}
}
