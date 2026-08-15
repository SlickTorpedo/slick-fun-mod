package com.slickfun.item;

import java.util.List;

import com.slickfun.util.ChairManager;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** Right click a block to sit on it. Sneak or jump to get up. */
public class ChairItem extends Item {
	/**
	 * A passenger rides at {@code EntityAttachmentType.PASSENGER}, whose default point is
	 * AT_HEIGHT - the vehicle's full height. An armour stand is 1.975 tall.
	 */
	private static final double ARMOR_STAND_PASSENGER_OFFSET = 1.975D;

	/** How far above the block's top surface the rider's feet end up. Tuned by eye. */
	private static final double SEAT_HEIGHT = 0.65D;

	public ChairItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();

		if (player == null) {
			return ActionResult.PASS;
		}

		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (player.hasVehicle() || !(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}

		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);
		VoxelShape shape = state.getCollisionShape(world, pos);

		if (shape.isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.chair.no_seat").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		// Don't wedge anyone into a ceiling.
		if (!world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()) {
			player.sendMessage(Text.translatable("message.slickfun.chair.no_room").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}

		double seatY = pos.getY() + shape.getMax(Direction.Axis.Y) - ARMOR_STAND_PASSENGER_OFFSET + SEAT_HEIGHT;

		if (!ChairManager.sit(serverPlayer, serverWorld, pos.getX() + 0.5D, seatY, pos.getZ() + 0.5D, player.getYaw())) {
			return ActionResult.FAIL;
		}

		serverWorld.playSound(null, pos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.5F, 1.1F);
		return ActionResult.SUCCESS;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.chair").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.chair.2").formatted(Formatting.DARK_GRAY));
	}
}
