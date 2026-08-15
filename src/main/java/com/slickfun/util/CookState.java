package com.slickfun.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** The three counters a furnace needs, stored on a portable cooker's item stack. */
public record CookState(int burnTime, int fuelTime, int cookTime) {
	public static final CookState EMPTY = new CookState(0, 0, 0);

	public static final Codec<CookState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("burn_time", 0).forGetter(CookState::burnTime),
			Codec.INT.optionalFieldOf("fuel_time", 0).forGetter(CookState::fuelTime),
			Codec.INT.optionalFieldOf("cook_time", 0).forGetter(CookState::cookTime)
	).apply(instance, CookState::new));

	public static final PacketCodec<ByteBuf, CookState> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CookState::burnTime,
			PacketCodecs.VAR_INT, CookState::fuelTime,
			PacketCodecs.VAR_INT, CookState::cookTime,
			CookState::new);

	public boolean isBurning() {
		return burnTime > 0;
	}
}
