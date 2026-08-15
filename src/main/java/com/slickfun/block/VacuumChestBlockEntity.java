package com.slickfun.block;

import java.util.List;

import com.slickfun.registry.ModBlocks;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Pulls in every loose item within six blocks.
 *
 * <p>It scans on an interval rather than every tick: the box query is the expensive part, and
 * at eight scans a second nothing perceptibly escapes it anyway.
 */
public class VacuumChestBlockEntity extends ChestLikeBlockEntity {
	public static final double RADIUS = 6.0D;

	private static final int INTERVAL_TICKS = 10;

	private int cooldown;

	public VacuumChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.VACUUM_CHEST_BLOCK_ENTITY, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, VacuumChestBlockEntity chest) {
		if (!(world instanceof ServerWorld serverWorld) || state.get(VacuumChestBlock.POWERED)) {
			return;
		}

		if (++chest.cooldown < INTERVAL_TICKS) {
			return;
		}

		chest.cooldown = 0;

		if (chest.isFull()) {
			return;
		}

		Vec3d centre = Vec3d.ofCenter(pos);
		Box area = new Box(centre, centre).expand(RADIUS);
		List<ItemEntity> loose = serverWorld.getEntitiesByClass(ItemEntity.class, area,
				item -> item.isAlive() && !item.cannotPickup());

		boolean took = false;

		for (ItemEntity item : loose) {
			// A copy, so the leftover is a different object and a partial intake is detectable.
			int before = item.getStack().getCount();
			ItemStack leftover = chest.accept(item.getStack().copy());

			if (leftover.getCount() == before) {
				continue;
			}

			serverWorld.spawnParticles(ParticleTypes.PORTAL,
					item.getX(), item.getY() + 0.2D, item.getZ(), 3, 0.1D, 0.1D, 0.1D, 0.0D);

			if (leftover.isEmpty()) {
				item.discard();
			} else {
				item.setStack(leftover);
			}

			took = true;
		}

		if (took) {
			serverWorld.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.3F, 1.6F);
			markDirty(world, pos, state);
		}
	}

	private boolean isFull() {
		for (ItemStack stack : this.inventory) {
			if (stack.isEmpty() || stack.getCount() < stack.getMaxCount()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("container.slickfun.vacuum_chest");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}
}
