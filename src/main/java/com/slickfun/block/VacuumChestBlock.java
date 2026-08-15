package com.slickfun.block;

import com.mojang.serialization.MapCodec;
import com.slickfun.registry.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The Item Magnet as a block: hoovers up loose items around it and stores them. */
public class VacuumChestBlock extends BlockWithEntity {
	public static final MapCodec<VacuumChestBlock> CODEC = createCodec(VacuumChestBlock::new);

	/** Toggled with redstone, so the intake can be switched off without breaking the block. */
	public static final BooleanProperty POWERED = Properties.POWERED;

	public VacuumChestBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(POWERED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new VacuumChestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient
				? null
				: validateTicker(type, ModBlocks.VACUUM_CHEST_BLOCK_ENTITY, VacuumChestBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (world.getBlockEntity(pos) instanceof NamedScreenHandlerFactory factory) {
			player.openHandledScreen(factory);
		}

		return ActionResult.CONSUME;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block source, BlockPos sourcePos, boolean notify) {
		if (world.isClient) {
			return;
		}

		boolean powered = world.isReceivingRedstonePower(pos);

		if (powered != state.get(POWERED)) {
			world.setBlockState(pos, state.with(POWERED, powered), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof VacuumChestBlockEntity chest) {
				ItemScatterer.spawn(world, pos, chest.contents());
				world.updateComparators(pos, this);
			}

			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof VacuumChestBlockEntity chest
				? ScreenHandler.calculateComparatorOutput((net.minecraft.inventory.Inventory) chest)
				: 0;
	}
}
