package com.slickfun.item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.slickfun.registry.ModComponents;
import com.slickfun.util.FakeDemolition;
import com.slickfun.util.ServerScheduler;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The self destruct that goes off, and destroys nothing at all.
 *
 * <p>The countdown is the whole trick. It runs down to 3, appears to abort and climb back up,
 * and then falls apart at speed - identical numbers, identical sound, identical colour the
 * whole way, because any tell at all in the climb gives the game away and the relief has to be
 * real before it is taken back.
 *
 * <p>Only the person who pressed it sees the damage, unless it has been fed ender eyes. That
 * is what makes it worth setting up: the presser watches the world end and everyone else
 * watches them react to nothing.
 */
public class RealSelfDestructButtonItem extends Item {
	/** Down to 3, back up to 7, and then it stops pretending. */
	private static final int[] SLOW = {10, 9, 8, 7, 6, 5, 4, 3, 4, 5, 6, 7};
	private static final int[] FAST = {6, 5, 4, 3, 2, 1};

	private static final int SLOW_STEP_TICKS = 20;
	private static final int FAST_STEP_TICKS = 4;

	/** Well past a normal render distance. It should look like nothing is left. */
	private static final int BLAST_RADIUS = 72;

	/** A hard ceiling on the illusion, for tick time and bandwidth. */
	private static final int MAX_BLOCKS = 150000;

	private static final int RESTORE_TICKS = 20 * 10;
	private static final int TNT_COUNT = 40;
	private static final int TNT_LEAD_TICKS = 20 * 3;
	private static final int VIEWER_RANGE = 192;

	/** Ender eyes needed before anyone else can see it happen. */
	public static final int EYE_COST = 16;

	public RealSelfDestructButtonItem(Settings settings) {
		super(settings);
	}

	public static int eyesIn(ItemStack stack) {
		return stack.getOrDefault(ModComponents.EYE_CHARGE, 0);
	}

	public static boolean isAllSeeing(ItemStack stack) {
		return eyesIn(stack) >= EYE_COST;
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

		if (player.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.fail(stack);
		}

		int total = SLOW.length * SLOW_STEP_TICKS + FAST.length * FAST_STEP_TICKS;
		player.getItemCooldownManager().set(this, total + RESTORE_TICKS + 40);

		boolean allSeeing = isAllSeeing(stack);
		MinecraftServer server = player.getServer();

		if (server != null) {
			server.getPlayerManager().broadcast(Text.translatable("message.slickfun.destruct.armed", player.getName())
					.formatted(Formatting.RED, Formatting.BOLD), false);
		}

		int elapsed = 0;

		for (int number : SLOW) {
			int at = elapsed;
			ServerScheduler.schedule(at, () -> announce(player, number));
			elapsed += SLOW_STEP_TICKS;
		}

		// The TNT turns up while the count is climbing, so it reads as a false alarm too.
		ServerScheduler.schedule(Math.max(0, elapsed - TNT_LEAD_TICKS), () -> prime(level, player, allSeeing));

		for (int number : FAST) {
			int at = elapsed;
			ServerScheduler.schedule(at, () -> announce(player, number));
			elapsed += FAST_STEP_TICKS;
		}

		ServerScheduler.schedule(elapsed, () -> detonate(level, player, allSeeing));

		return TypedActionResult.success(stack);
	}

	/** Feeds it ender eyes so everyone nearby sees the destruction, not just the presser. */
	private static TypedActionResult<ItemStack> feed(ServerPlayerEntity player, ItemStack stack) {
		int wanted = EYE_COST - eyesIn(stack);

		if (wanted <= 0) {
			player.sendMessage(Text.translatable("message.slickfun.destruct.already_seeing").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		int taken = 0;

		for (int slot = 0; slot < player.getInventory().size() && taken < wanted; slot++) {
			ItemStack held = player.getInventory().getStack(slot);

			if (!held.isOf(Items.ENDER_EYE)) {
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
			player.sendMessage(Text.translatable("message.slickfun.destruct.need_eyes", wanted).formatted(Formatting.GRAY), false);
			return TypedActionResult.fail(stack);
		}

		stack.set(ModComponents.EYE_CHARGE, eyesIn(stack) + taken);

		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.sendMessage(Text.translatable(isAllSeeing(stack)
						? "message.slickfun.destruct.all_seeing"
						: "message.slickfun.destruct.eyes_fed", eyesIn(stack), EYE_COST)
				.formatted(Formatting.LIGHT_PURPLE), false);

		return TypedActionResult.success(stack);
	}

	/**
	 * Every number looks and sounds exactly the same, going up or coming down. The moment the
	 * climb is distinguishable from the fall, the joke stops working.
	 */
	private static void announce(ServerPlayerEntity player, int number) {
		if (player.isRemoved()) {
			return;
		}

		ServerWorld level = player.getServerWorld();

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 1.4F, 0.8F);

		Text shout = Text.literal(String.valueOf(number)).formatted(Formatting.RED, Formatting.BOLD);

		for (ServerPlayerEntity nearby : level.getPlayers(other ->
				other.getPos().squaredDistanceTo(player.getPos()) <= 64.0D * 64.0D)) {
			nearby.sendMessage(shout, true);
		}
	}

	/** Fake TNT, sitting there doing nothing, right up until it appears to go off. */
	private static void prime(ServerWorld level, ServerPlayerEntity player, boolean allSeeing) {
		if (player.isRemoved()) {
			return;
		}

		List<ServerPlayerEntity> viewers = audience(level, player, allSeeing);
		var tnt = FakeDemolition.scatterTnt(level, player.getBlockPos(), 24, TNT_COUNT);

		if (tnt.isEmpty()) {
			return;
		}

		FakeDemolition.show(level, viewers, tnt);
		level.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 3.0F, 1.0F);

		PRIMED.put(player.getUuid(), new ArrayList<>(tnt.keySet()));
	}

	private static final java.util.Map<java.util.UUID, List<BlockPos>> PRIMED = new java.util.HashMap<>();

	private static void detonate(ServerWorld level, ServerPlayerEntity player, boolean allSeeing) {
		if (player.isRemoved()) {
			return;
		}

		BlockPos centre = player.getBlockPos();
		// Left standing, so the presser is not apparently hovering over a void.
		BlockPos footing = centre.down();

		List<ServerPlayerEntity> viewers = audience(level, player, allSeeing);
		java.util.Map<BlockPos, BlockState> crater =
				FakeDemolition.crater(level, centre, BLAST_RADIUS, footing, MAX_BLOCKS);

		FakeDemolition.show(level, viewers, crater);
		FakeDemolition.spectacle(level, centre, BLAST_RADIUS);
		FakeDemolition.barrage(level, centre, BLAST_RADIUS, 14, 12);

		// The primed TNT is part of the same illusion, so it has to be put back with the rest.
		Set<BlockPos> everything = new HashSet<>(crater.keySet());
		List<BlockPos> tnt = PRIMED.remove(player.getUuid());

		if (tnt != null) {
			everything.addAll(tnt);
		}

		ServerScheduler.schedule(RESTORE_TICKS, () -> FakeDemolition.restore(level, viewers, everything));
	}

	private static List<ServerPlayerEntity> audience(ServerWorld level, ServerPlayerEntity player, boolean allSeeing) {
		return allSeeing
				? FakeDemolition.viewersWithin(level, player.getBlockPos(), VIEWER_RANGE)
				: new ArrayList<>(List.of(player));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		// Nothing here admits the destruction is an illusion. The reveal is the world coming
		// back, and a tooltip that gives it away beforehand costs the whole effect.
		tooltip.add(Text.translatable("tooltip.slickfun.real_self_destruct").formatted(Formatting.RED));

		if (isAllSeeing(stack)) {
			tooltip.add(Text.translatable("tooltip.slickfun.real_self_destruct.seeing").formatted(Formatting.LIGHT_PURPLE));
		} else {
			tooltip.add(Text.translatable("tooltip.slickfun.real_self_destruct.eyes", eyesIn(stack), EYE_COST)
					.formatted(Formatting.DARK_GRAY));
		}
	}
}
