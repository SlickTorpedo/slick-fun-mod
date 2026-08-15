package com.slickfun.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * One kind of item, and a great many of it.
 *
 * <p>The sample is a whole {@link ItemStack} rather than just an item id so that everything
 * distinguishing one variant from another survives - a firework's flight duration and its
 * explosions, an enchanted book's enchantments, a renamed tool's name. Storing only the id
 * would quietly turn a stack of three-stage rockets into plain ones.
 *
 * <p>The sample's own count is meaningless and always left at one; {@link #count} is the real
 * total, and it is free to run far past a normal stack.
 */
public record BulkStore(ItemStack sample, int count) {
	public static final BulkStore EMPTY = new BulkStore(ItemStack.EMPTY, 0);

	public static final Codec<BulkStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("sample", ItemStack.EMPTY).forGetter(BulkStore::sample),
			Codec.INT.optionalFieldOf("count", 0).forGetter(BulkStore::count)
	).apply(instance, BulkStore::new));

	public static final PacketCodec<RegistryByteBuf, BulkStore> PACKET_CODEC = PacketCodec.tuple(
			ItemStack.OPTIONAL_PACKET_CODEC, BulkStore::sample,
			PacketCodecs.VAR_INT, BulkStore::count,
			BulkStore::new);

	public boolean isEmpty() {
		return this.count <= 0 || this.sample.isEmpty();
	}

	/** Whether this store would take that stack. An empty store takes the first thing offered. */
	public boolean accepts(ItemStack other) {
		if (other.isEmpty()) {
			return false;
		}

		return isEmpty() || ItemStack.areItemsAndComponentsEqual(this.sample, other);
	}

	/** A single item of whatever is stored, ready to hand out. */
	public ItemStack one() {
		return this.sample.isEmpty() ? ItemStack.EMPTY : this.sample.copyWithCount(1);
	}

	public ItemStack take(int wanted) {
		int moved = Math.min(wanted, this.count);
		return moved <= 0 ? ItemStack.EMPTY : this.sample.copyWithCount(moved);
	}

	public BulkStore with(ItemStack incoming, int added, int capacity) {
		ItemStack template = this.sample.isEmpty() ? incoming.copyWithCount(1) : this.sample;
		// Saturating rather than wrapping: a very full store must never go negative.
		long total = (long) this.count + added;
		return new BulkStore(template, (int) Math.min(capacity, total));
	}

	/** Emptying it forgets what it held, so it can be locked to something else. */
	public BulkStore less(int removed) {
		int left = Math.max(0, this.count - removed);
		return left == 0 ? EMPTY : new BulkStore(this.sample, left);
	}
}
