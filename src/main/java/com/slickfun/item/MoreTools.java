package com.slickfun.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.slickfun.registry.ModComponents;
import com.slickfun.util.ServerScheduler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/** Batch four: finding, restocking, shaping, herding and distracting. */
public final class MoreTools {
	private MoreTools() {
	}

	/** Sneak on an ore to mark it, then it points at the nearest one it can find. */
	public static class OreCompass extends Item {
		private static final int RADIUS = 24;
		private static final int COOLDOWN_TICKS = 40;

		public OreCompass(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !player.isSneaking()) {
				return ActionResult.PASS;
			}

			Block block = world.getBlockState(context.getBlockPos()).getBlock();
			Identifier id = Registries.BLOCK.getId(block);

			context.getStack().set(ModComponents.TARGET_BLOCK, id);
			context.getStack().remove(DataComponentTypes.LODESTONE_TRACKER);
			player.sendMessage(Text.translatable("message.slickfun.ore.marked", block.getName()).formatted(Formatting.AQUA), true);

			return ActionResult.SUCCESS;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			Identifier wanted = stack.get(ModComponents.TARGET_BLOCK);

			if (wanted == null) {
				player.sendMessage(Text.translatable("message.slickfun.ore.unset").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			Block target = Registries.BLOCK.get(wanted);
			BlockPos origin = player.getBlockPos();
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;

			for (BlockPos pos : BlockPos.iterate(origin.add(-RADIUS, -RADIUS, -RADIUS), origin.add(RADIUS, RADIUS, RADIUS))) {
				if (!serverWorld.getBlockState(pos).isOf(target)) {
					continue;
				}

				double distance = pos.getSquaredDistance(player.getPos());

				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.toImmutable();
				}
			}

			if (best == null) {
				player.sendMessage(Text.translatable("message.slickfun.ore.none", target.getName(), RADIUS).formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			stack.set(DataComponentTypes.LODESTONE_TRACKER,
					new LodestoneTrackerComponent(Optional.of(GlobalPos.create(serverWorld.getRegistryKey(), best)), false));

			for (int height = 0; height < 5; height++) {
				serverWorld.spawnParticles(ParticleTypes.END_ROD,
						best.getX() + 0.5D, best.getY() + 1.0D + height * 0.6D, best.getZ() + 0.5D, 2, 0.1D, 0.1D, 0.1D, 0.0D);
			}

			player.sendMessage(Text.translatable("message.slickfun.ore.found",
					target.getName(), best.getX(), best.getY(), best.getZ(), (int) Math.sqrt(bestDistance)).formatted(Formatting.AQUA), false);
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.6F, 1.8F);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			Identifier wanted = stack.get(ModComponents.TARGET_BLOCK);

			tooltip.add(Text.translatable("tooltip.slickfun.ore_compass.1", RADIUS).formatted(Formatting.GRAY));
			tooltip.add(wanted == null
					? Text.translatable("tooltip.slickfun.ore_compass.unset").formatted(Formatting.DARK_GRAY)
					: Text.translatable("tooltip.slickfun.ore_compass.set", Registries.BLOCK.get(wanted).getName()).formatted(Formatting.AQUA));
		}
	}

	/** Tops up partial stacks in your inventory from a container. */
	public static class RestockWand extends Item {
		public RestockWand(Settings settings) {
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

			if (!(world.getBlockEntity(context.getBlockPos()) instanceof Inventory container)) {
				player.sendMessage(Text.translatable("message.slickfun.sort.not_a_container").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			int moved = 0;

			for (int mine = 0; mine < player.getInventory().size(); mine++) {
				ItemStack held = player.getInventory().getStack(mine);

				if (held.isEmpty() || held.getCount() >= held.getMaxCount()) {
					continue;
				}

				for (int theirs = 0; theirs < container.size() && held.getCount() < held.getMaxCount(); theirs++) {
					ItemStack stored = container.getStack(theirs);

					if (stored.isEmpty() || !ItemStack.areItemsAndComponentsEqual(held, stored)) {
						continue;
					}

					int take = Math.min(stored.getCount(), held.getMaxCount() - held.getCount());
					held.increment(take);
					stored.decrement(take);
					moved += take;
				}
			}

			if (moved == 0) {
				player.sendMessage(Text.translatable("message.slickfun.restock.nothing").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			container.markDirty();
			world.playSound(null, context.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6F, 1.2F);
			player.sendMessage(Text.translatable("message.slickfun.restock.done", moved).formatted(Formatting.GRAY), true);

			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.restock_wand").formatted(Formatting.GRAY));
		}
	}

	/**
	 * Cycles a block through its own family in place.
	 *
	 * <p>The families are listed rather than derived, because there is no rule connecting
	 * stone to its bricks the way there is for dyed blocks.
	 */
	public static class Chisel extends Item {
		private static final Block[][] FAMILIES = {
				{Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SMOOTH_STONE,
						Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS},
				{Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE},
				{Blocks.RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE},
				{Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
						Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE},
				{Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS, Blocks.RED_NETHER_BRICKS},
				{Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS},
				{Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE},
				{Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
						Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE},
				{Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
						Blocks.CUT_COPPER, Blocks.CHISELED_COPPER},
				{Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS},
				{Blocks.MUD_BRICKS, Blocks.PACKED_MUD},
				{Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR},
				{Blocks.END_STONE, Blocks.END_STONE_BRICKS}
		};

		public Chisel(Settings settings) {
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

			BlockPos pos = context.getBlockPos();
			BlockState current = world.getBlockState(pos);

			for (Block[] family : FAMILIES) {
				for (int index = 0; index < family.length; index++) {
					if (!current.isOf(family[index])) {
						continue;
					}

					// Sneak walks the family backwards.
					int step = player.isSneaking() ? -1 : 1;
					Block next = family[Math.floorMod(index + step, family.length)];

					world.setBlockState(pos, next.getStateWithProperties(current), Block.NOTIFY_ALL);
					world.playSound(null, pos, SoundEvents.ITEM_AXE_SCRAPE, SoundCategory.BLOCKS, 0.8F, 1.0F);
					context.getStack().damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);

					return ActionResult.SUCCESS;
				}
			}

			player.sendMessage(Text.translatable("message.slickfun.chisel.no_family").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.chisel.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.chisel.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Pairs up every animal nearby that is ready, using food from your inventory. */
	public static class BreedingWhistle extends Item {
		private static final double RADIUS = 8.0D;
		private static final int COOLDOWN_TICKS = 60;

		public BreedingWhistle(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			Box area = player.getBoundingBox().expand(RADIUS);
			List<AnimalEntity> ready = new ArrayList<>();

			for (AnimalEntity animal : serverWorld.getEntitiesByClass(AnimalEntity.class, area, AnimalEntity::isAlive)) {
				if (!animal.isBaby() && !animal.isInLove() && animal.getBreedingAge() == 0) {
					ready.add(animal);
				}
			}

			int paired = 0;

			for (AnimalEntity animal : ready) {
				int slot = findFood(player, animal);

				if (slot < 0) {
					continue;
				}

				player.getInventory().getStack(slot).decrement(1);
				animal.lovePlayer(player);
				serverWorld.spawnParticles(ParticleTypes.HEART,
						animal.getX(), animal.getBodyY(1.0D), animal.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
				paired++;
			}

			if (paired == 0) {
				player.sendMessage(Text.translatable("message.slickfun.whistle.nothing").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 0.8F, 1.6F);
			player.sendMessage(Text.translatable("message.slickfun.whistle.fed", paired).formatted(Formatting.GRAY), true);
			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			return TypedActionResult.success(stack, false);
		}

		private static int findFood(ServerPlayerEntity player, AnimalEntity animal) {
			for (int slot = 0; slot < player.getInventory().size(); slot++) {
				ItemStack candidate = player.getInventory().getStack(slot);

				if (!candidate.isEmpty() && animal.isBreedingItem(candidate)) {
					return slot;
				}
			}

			return -1;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.breeding_whistle.1", (int) RADIUS).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.breeding_whistle.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Drops something more interesting than you for mobs to hit. */
	public static class Decoy extends Item {
		private static final double RADIUS = 16.0D;
		private static final int LIFETIME_TICKS = 20 * 15;
		private static final int COOLDOWN_TICKS = 20 * 30;

		public Decoy(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			ArmorStandEntity decoy = EntityType.ARMOR_STAND.create(serverWorld);

			if (decoy == null) {
				return TypedActionResult.fail(stack);
			}

			decoy.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0F);
			decoy.setCustomName(Text.translatable("entity.slickfun.decoy", player.getDisplayName()));
			decoy.setCustomNameVisible(true);
			decoy.setNoGravity(false);
			decoy.addCommandTag("slickfun_decoy");
			serverWorld.spawnEntity(decoy);

			int pulled = redirect(serverWorld, player, decoy);

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ARMOR_STAND_PLACE, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.decoy", pulled).formatted(Formatting.GRAY), true);

			// Keep pulling attention for as long as it stands.
			retarget(serverWorld, player, decoy, LIFETIME_TICKS / 20);

			ServerScheduler.schedule(LIFETIME_TICKS, () -> {
				if (!decoy.isRemoved()) {
					serverWorld.spawnParticles(ParticleTypes.POOF, decoy.getX(), decoy.getBodyY(0.5D), decoy.getZ(),
							20, 0.3D, 0.5D, 0.3D, 0.02D);
					decoy.discard();
				}
			});

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			if (!player.isCreative()) {
				stack.decrement(1);
			}

			return TypedActionResult.success(stack, false);
		}

		private static void retarget(ServerWorld world, ServerPlayerEntity player, ArmorStandEntity decoy, int rounds) {
			if (rounds <= 0 || decoy.isRemoved()) {
				return;
			}

			ServerScheduler.schedule(20, () -> {
				if (decoy.isRemoved()) {
					return;
				}

				redirect(world, player, decoy);
				retarget(world, player, decoy, rounds - 1);
			});
		}

		private static int redirect(ServerWorld world, ServerPlayerEntity player, ArmorStandEntity decoy) {
			Box area = decoy.getBoundingBox().expand(RADIUS);
			int pulled = 0;

			for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, area, LivingEntity::isAlive)) {
				if (mob.getTarget() == null || mob.getTarget() == player) {
					mob.setTarget(decoy);
					pulled++;
				}
			}

			return pulled;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.decoy.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.decoy.2").formatted(Formatting.DARK_GRAY));
		}
	}
}
