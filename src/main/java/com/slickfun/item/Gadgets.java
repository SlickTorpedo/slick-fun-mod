package com.slickfun.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.slickfun.util.ServerScheduler;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * Small tools that answer a question. None of them change the world - they all just draw
 * particles or print a line, which means they need no client-side code at all.
 */
public final class Gadgets {
	private Gadgets() {
	}

	/** Shared shape for the "right click, get an answer" gadgets. */
	private abstract static class Gadget extends Item {
		private final String key;
		private final int cooldown;

		Gadget(Settings settings, String key, int cooldown) {
			super(settings);
			this.key = key;
			this.cooldown = cooldown;
		}

		abstract void run(ServerPlayerEntity player, ServerWorld world);

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			run(player, serverWorld);
			player.getItemCooldownManager().set(this, cooldown);
			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun." + this.key).formatted(Formatting.GRAY));
		}
	}

	/**
	 * Marks every block a hostile mob could spawn on. Runs server side and draws particles,
	 * so it works for vanilla clients too.
	 */
	public static class LightMeter extends Gadget {
		private static final int RADIUS = 12;
		private static final int SHOWS = 5;

		public LightMeter(Settings settings) {
			super(settings, "light_meter", 100);
		}

		@Override
		void run(ServerPlayerEntity player, ServerWorld world) {
			player.sendMessage(Text.translatable("message.slickfun.light_meter").formatted(Formatting.GRAY), true);

			for (int repeat = 0; repeat < SHOWS; repeat++) {
				ServerScheduler.schedule(repeat * 20 + 1, () -> {
					if (player.isRemoved()) {
						return;
					}

					BlockPos origin = player.getBlockPos();

					for (BlockPos pos : BlockPos.iterate(origin.add(-RADIUS, -4, -RADIUS), origin.add(RADIUS, 4, RADIUS))) {
						if (isSpawnable(world, pos)) {
							world.spawnParticles(ParticleTypes.FLAME,
									pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
						}
					}
				});
			}
		}

		private static boolean isSpawnable(ServerWorld world, BlockPos pos) {
			// Since 1.18 hostiles need block light 0, not just darkness.
			return world.getLightLevel(LightType.BLOCK, pos) == 0
					&& world.getBlockState(pos).isAir()
					&& world.getBlockState(pos.up()).isAir()
					&& world.getBlockState(pos.down()).isOpaqueFullCube(world, pos.down());
		}
	}

	/** Outlines the chunk you are standing in. */
	public static class ChunkBorderStick extends Gadget {
		private static final int SHOWS = 8;

		public ChunkBorderStick(Settings settings) {
			super(settings, "chunk_border_stick", 60);
		}

		@Override
		void run(ServerPlayerEntity player, ServerWorld world) {
			ChunkPos chunk = new ChunkPos(player.getBlockPos());
			player.sendMessage(Text.translatable("message.slickfun.chunk_border", chunk.x, chunk.z)
					.formatted(Formatting.GRAY), true);

			for (int repeat = 0; repeat < SHOWS; repeat++) {
				ServerScheduler.schedule(repeat * 10 + 1, () -> {
					if (player.isRemoved()) {
						return;
					}

					double baseY = player.getY() - 2.0D;

					for (int step = 0; step <= 16; step++) {
						for (int height = 0; height < 6; height++) {
							double y = baseY + height;
							corner(world, chunk.getStartX() + step, y, chunk.getStartZ());
							corner(world, chunk.getStartX() + step, y, chunk.getStartZ() + 16);
							corner(world, chunk.getStartX(), y, chunk.getStartZ() + step);
							corner(world, chunk.getStartX() + 16, y, chunk.getStartZ() + step);
						}
					}
				});
			}
		}

		private static void corner(ServerWorld world, double x, double y, double z) {
			world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Counts everything alive nearby, so you can find what is dragging the tick rate down. */
	public static class MobCensus extends Gadget {
		private static final double RANGE = 64.0D;

		public MobCensus(Settings settings) {
			super(settings, "mob_census", 40);
		}

		@Override
		void run(ServerPlayerEntity player, ServerWorld world) {
			Box area = player.getBoundingBox().expand(RANGE);
			Map<String, Integer> tally = new TreeMap<>();

			for (Entity entity : world.getOtherEntities(player, area, entity -> !(entity instanceof PlayerEntity))) {
				String name = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
				tally.merge(name, 1, Integer::sum);
			}

			if (tally.isEmpty()) {
				player.sendMessage(Text.translatable("message.slickfun.census.empty").formatted(Formatting.GRAY), false);
				return;
			}

			int total = tally.values().stream().mapToInt(Integer::intValue).sum();
			player.sendMessage(Text.translatable("message.slickfun.census.header", total, (int) RANGE)
					.formatted(Formatting.AQUA, Formatting.BOLD), false);

			tally.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
					.limit(12)
					.forEach(entry -> player.sendMessage(Text.literal("  " + entry.getValue() + "x ")
							.formatted(Formatting.YELLOW)
							.append(Text.literal(entry.getKey()).formatted(Formatting.GRAY)), false));
		}
	}

	/** Time, moon phase, and how close you are to phantoms. */
	public static class TideClock extends Gadget {
		private static final int PHANTOM_THRESHOLD = 72000;

		public TideClock(Settings settings) {
			super(settings, "tide_clock", 20);
		}

		@Override
		void run(ServerPlayerEntity player, ServerWorld world) {
			long time = world.getTimeOfDay() % 24000L;
			long hour = (time / 1000L + 6L) % 24L;
			long minute = (time % 1000L) * 60L / 1000L;

			player.sendMessage(Text.translatable("message.slickfun.clock.time",
					String.format("%02d:%02d", hour, minute)).formatted(Formatting.AQUA), false);
			player.sendMessage(Text.translatable("message.slickfun.clock.moon", world.getMoonPhase())
					.formatted(Formatting.GRAY), false);

			int sinceRest = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));

			if (sinceRest >= PHANTOM_THRESHOLD) {
				player.sendMessage(Text.translatable("message.slickfun.clock.phantoms_now").formatted(Formatting.RED), false);
			} else {
				long nights = (PHANTOM_THRESHOLD - sinceRest) / 24000L;
				player.sendMessage(Text.translatable("message.slickfun.clock.phantoms_in", nights)
						.formatted(Formatting.GRAY), false);
			}
		}
	}

	/**
	 * Reads a spawner without breaking it. Pulls the numbers out of the block entity's own
	 * save data rather than its private fields.
	 */
	public static class SpawnerReader extends Item {
		public SpawnerReader(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
				return ActionResult.FAIL;
			}

			BlockEntity blockEntity = world.getBlockEntity(context.getBlockPos());

			if (!(blockEntity instanceof MobSpawnerBlockEntity spawner)) {
				player.sendMessage(Text.translatable("message.slickfun.spawner.not_a_spawner").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			NbtCompound nbt = spawner.createNbt(world.getRegistryManager());
			String type = "unknown";

			if (nbt.contains("SpawnData")) {
				NbtCompound entity = nbt.getCompound("SpawnData").getCompound("entity");
				type = entity.contains("id") ? entity.getString("id") : type;
			}

			player.sendMessage(Text.translatable("message.slickfun.spawner.header").formatted(Formatting.AQUA, Formatting.BOLD), false);
			line(player, "message.slickfun.spawner.type", type);
			line(player, "message.slickfun.spawner.delay", nbt.getShort("Delay"));
			line(player, "message.slickfun.spawner.range", nbt.getShort("RequiredPlayerRange"));
			line(player, "message.slickfun.spawner.count", nbt.getShort("SpawnCount"));
			line(player, "message.slickfun.spawner.max", nbt.getShort("MaxNearbyEntities"));

			world.playSound(null, context.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 1.2F);
			return ActionResult.SUCCESS;
		}

		private static void line(ServerPlayerEntity player, String key, Object value) {
			player.sendMessage(Text.translatable(key, value).formatted(Formatting.GRAY), false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.spawner_reader").formatted(Formatting.GRAY));
		}
	}

	/** Shouts at you before your gear breaks. Works from anywhere in your inventory. */
	public static class DurabilityAlarm extends Item {
		private static final float WARN_AT = 0.25F;
		private static final float CRITICAL_AT = 0.10F;
		private static final int QUIET_TICKS = 120;
		private static final Map<UUID, Long> LAST_WARNED = new HashMap<>();

		public DurabilityAlarm(Settings settings) {
			super(settings);
		}

		@Override
		public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
			if (world.isClient || world.getTime() % 20L != 0L || !(entity instanceof ServerPlayerEntity player)) {
				return;
			}

			Long last = LAST_WARNED.get(player.getUuid());

			if (last != null && world.getTime() - last < QUIET_TICKS) {
				return;
			}

			for (EquipmentSlot equipment : EquipmentSlot.values()) {
				ItemStack worn = player.getEquippedStack(equipment);

				if (worn.isEmpty() || !worn.isDamageable()) {
					continue;
				}

				int remaining = worn.getMaxDamage() - worn.getDamage();

				if (remaining > worn.getMaxDamage() * WARN_AT) {
					continue;
				}

				boolean critical = remaining <= worn.getMaxDamage() * CRITICAL_AT;
				LAST_WARNED.put(player.getUuid(), world.getTime());
				warn(player, worn, remaining, critical);
				return;
			}
		}

		private static void warn(ServerPlayerEntity player, ItemStack worn, int remaining, boolean critical) {
			Formatting colour = critical ? Formatting.RED : Formatting.GOLD;

			// Titles are their own packets - there is no Text helper that puts one on screen.
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(4, 30, 8));
			player.networkHandler.sendPacket(new TitleS2CPacket(
					Text.translatable(critical ? "title.slickfun.durability.critical" : "title.slickfun.durability.low")
							.formatted(colour, Formatting.BOLD)));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(
					Text.translatable("title.slickfun.durability.sub", worn.getName(), remaining).formatted(Formatting.GRAY)));

			player.sendMessage(Text.translatable("message.slickfun.durability", worn.getName(), remaining)
					.formatted(colour), false);

			World world = player.getWorld();

			if (critical) {
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.7F, 1.6F);
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 0.8F);
			} else {
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0F, 0.6F);
			}

			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.6F, 1.8F);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.durability_alarm").formatted(Formatting.GRAY));
		}

		/** Keeps the map from growing forever on a long-running server. */
		public static void forget(LivingEntity entity) {
			LAST_WARNED.remove(entity.getUuid());
		}
	}
}
