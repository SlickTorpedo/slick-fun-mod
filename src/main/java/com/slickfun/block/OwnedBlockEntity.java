package com.slickfun.block;

import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** A chest-shaped block that remembers who put it down. */
public abstract class OwnedBlockEntity extends ChestLikeBlockEntity {
	private UUID owner;
	private String ownerName = "";

	protected OwnedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void setOwner(PlayerEntity player) {
		this.owner = player.getUuid();
		this.ownerName = player.getGameProfile().getName();
		markDirty();
	}

	public UUID owner() {
		return this.owner;
	}

	public Text ownerName() {
		return this.ownerName.isEmpty() ? Text.translatable("message.slickfun.owner.unknown") : Text.literal(this.ownerName);
	}

	/** An unclaimed block belongs to whoever is standing at it, so it never becomes a brick. */
	public boolean isOwner(PlayerEntity player) {
		return this.owner == null || this.owner.equals(player.getUuid());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		this.ownerName = nbt.getString("OwnerName");
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);

		if (this.owner != null) {
			nbt.putUuid("Owner", this.owner);
		}

		nbt.putString("OwnerName", this.ownerName);
	}
}
