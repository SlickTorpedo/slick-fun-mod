package com.slickfun.block;

import com.slickfun.registry.ModBlocks;
import com.slickfun.screen.MailBoxScreenHandler;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class MailBoxBlockEntity extends OwnedBlockEntity {
	public MailBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.MAIL_BOX_BLOCK_ENTITY, pos, state);
	}

	/** A hopper under the box would otherwise be a way to read someone else's mail. */
	@Override
	protected boolean hoppersMayEmpty() {
		return false;
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("container.slickfun.mail_box", ownerName());
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return MailBoxScreenHandler.create(syncId, playerInventory, this, isOwner(player));
	}
}
