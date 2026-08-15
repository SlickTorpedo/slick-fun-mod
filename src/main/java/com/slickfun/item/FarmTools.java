package com.slickfun.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Tools for the parts of farming that are pure repetition. */
public final class FarmTools {
	private FarmTools() {
	}

	/**
	 * Harvests and replants every mature crop in a 3x3.
	 *
	 * <p>One seed is taken out of the drops per crop to pay for the replant, so this is a
	 * convenience rather than a way to farm without seeds.
	 */
	public static class HarvestSickle extends Item {
		private static final int RADIUS = 1;

		public HarvestSickle(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(world instanceof ServerWorld serverWorld) || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
				return ActionResult.FAIL;
			}

			BlockPos centre = context.getBlockPos();
			int harvested = 0;

			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					if (harvest(serverWorld, centre.add(dx, 0, dz))) {
						harvested++;
					}
				}
			}

			if (harvested == 0) {
				return ActionResult.FAIL;
			}

			serverWorld.playSound(null, centre, SoundEvents.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.sickle", harvested).formatted(Formatting.GRAY), true);
			context.getStack().damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);

			return ActionResult.SUCCESS;
		}

		private static boolean harvest(ServerWorld world, BlockPos pos) {
			BlockState state = world.getBlockState(pos);
			BlockState replanted;
			Item seed;

			if (state.getBlock() instanceof CropBlock crop) {
				if (!crop.isMature(state)) {
					return false;
				}

				replanted = crop.withAge(0);
				seed = null;
			} else if (state.getBlock() instanceof NetherWartBlock) {
				IntProperty age = NetherWartBlock.AGE;

				if (state.get(age) < 3) {
					return false;
				}

				replanted = state.with(age, 0);
				seed = Items.NETHER_WART;
			} else {
				return false;
			}

			List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, world.getBlockEntity(pos));
			boolean seedPaid = false;

			for (ItemStack drop : drops) {
				// Withhold one seed to cover the replant.
				if (!seedPaid && (seed == null || drop.isOf(seed))) {
					if (drop.getCount() > 0) {
						drop.decrement(1);
						seedPaid = true;
					}
				}

				if (!drop.isEmpty()) {
					Block.dropStack(world, pos, drop);
				}
			}

			world.setBlockState(pos, replanted, Block.NOTIFY_ALL);
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
					pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D, 3, 0.3D, 0.2D, 0.3D, 0.0D);
			return true;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.harvest_sickle.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.harvest_sickle.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Bone meal across a 5x5, straight from your inventory. */
	public static class BonemealSprayer extends Item {
		private static final int RADIUS = 2;

		public BonemealSprayer(Settings settings) {
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

			int available = player.isCreative() ? 25 : BuildingTools.countIn(player, Items.BONE_MEAL);

			if (available <= 0) {
				player.sendMessage(Text.translatable("message.slickfun.sprayer.empty").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			BlockPos centre = context.getBlockPos();
			int used = 0;

			for (int dx = -RADIUS; dx <= RADIUS && used < available; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS && used < available; dz++) {
					ItemStack fuel = new ItemStack(Items.BONE_MEAL);

					if (BoneMealItem.useOnFertilizable(fuel, world, centre.add(dx, 0, dz))) {
						used++;
					}
				}
			}

			if (used == 0) {
				return ActionResult.FAIL;
			}

			if (!player.isCreative()) {
				BuildingTools.consume(player, Items.BONE_MEAL, used);
			}

			world.playSound(null, centre, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.sprayer.used", used).formatted(Formatting.GRAY), true);
			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.bonemeal_sprayer.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.bonemeal_sprayer.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Empties every compostable in your inventory into a composter. */
	public static class ComposterWand extends Item {
		public ComposterWand(Settings settings) {
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
			BlockState state = world.getBlockState(pos);

			if (!state.isOf(Blocks.COMPOSTER)) {
				return ActionResult.PASS;
			}

			int level = state.get(ComposterBlock.LEVEL);
			int fed = 0;

			for (int slot = 0; slot < player.getInventory().size() && level < ComposterBlock.MAX_LEVEL; slot++) {
				ItemStack stack = player.getInventory().getStack(slot);

				if (stack.isEmpty() || !ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.containsKey(stack.getItem())) {
					continue;
				}

				float chance = ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(stack.getItem());

				while (!stack.isEmpty() && level < ComposterBlock.MAX_LEVEL) {
					stack.decrement(1);
					fed++;

					if (world.getRandom().nextFloat() < chance) {
						level++;
					}
				}
			}

			if (fed == 0) {
				player.sendMessage(Text.translatable("message.slickfun.composter.nothing").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			world.setBlockState(pos, state.with(ComposterBlock.LEVEL, level), Block.NOTIFY_ALL);
			world.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_FILL_SUCCESS, SoundCategory.BLOCKS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.composter.fed", fed).formatted(Formatting.GRAY), true);

			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.composter_wand").formatted(Formatting.GRAY));
		}
	}

	/**
	 * Picks up a beehive with the bees still inside.
	 *
	 * <p>Copies the block entity into the dropped item the same way silk touch does, so the
	 * colony survives the move.
	 */
	public static class BeehiveTool extends Item {
		public BeehiveTool(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(world instanceof ServerWorld serverWorld) || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
				return ActionResult.FAIL;
			}

			BlockPos pos = context.getBlockPos();
			BlockState state = world.getBlockState(pos);

			if (!state.isOf(Blocks.BEEHIVE) && !state.isOf(Blocks.BEE_NEST)) {
				return ActionResult.PASS;
			}

			ItemStack hive = new ItemStack(state.getBlock());
			BlockEntity blockEntity = world.getBlockEntity(pos);

			if (blockEntity != null) {
				NbtCompound nbt = blockEntity.createNbtWithIdentifyingData(world.getRegistryManager());
				hive.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(nbt));
			}

			serverWorld.removeBlock(pos, false);
			Block.dropStack(world, pos, hive);
			world.playSound(null, pos, SoundEvents.BLOCK_BEEHIVE_EXIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.beehive").formatted(Formatting.GRAY), true);

			return ActionResult.SUCCESS;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.beehive_tool.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.beehive_tool.2").formatted(Formatting.DARK_GRAY));
		}
	}
}
