package com.slickfun.util;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;

/**
 * Who a Soul Tie is tied to, and what it has been fed.
 *
 * <p>The two charges are what let the tie reach past the overworld. They are stored as counts
 * rather than booleans so a partly fed tie can say how far along it is.
 */
public record SoulBond(Optional<UUID> partner, String partnerName, int pearls, int netherrack) {
	/** Ender pearls to reach someone standing in the End. */
	public static final int PEARL_COST = 144;

	/** Netherrack to reach someone standing in the Nether. */
	public static final int NETHERRACK_COST = 576;

	public static final SoulBond EMPTY = new SoulBond(Optional.empty(), "", 0, 0);

	public static final Codec<SoulBond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Uuids.CODEC.optionalFieldOf("partner").forGetter(SoulBond::partner),
			Codec.STRING.optionalFieldOf("partner_name", "").forGetter(SoulBond::partnerName),
			Codec.INT.optionalFieldOf("pearls", 0).forGetter(SoulBond::pearls),
			Codec.INT.optionalFieldOf("netherrack", 0).forGetter(SoulBond::netherrack)
	).apply(instance, SoulBond::new));

	public static final PacketCodec<RegistryByteBuf, SoulBond> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.optional(Uuids.PACKET_CODEC), SoulBond::partner,
			PacketCodecs.STRING, SoulBond::partnerName,
			PacketCodecs.VAR_INT, SoulBond::pearls,
			PacketCodecs.VAR_INT, SoulBond::netherrack,
			SoulBond::new);

	public boolean isBound() {
		return this.partner.isPresent();
	}

	public boolean isBoundTo(UUID other) {
		return this.partner.isPresent() && this.partner.get().equals(other);
	}

	public boolean reachesEnd() {
		return this.pearls >= PEARL_COST;
	}

	public boolean reachesNether() {
		return this.netherrack >= NETHERRACK_COST;
	}

	/**
	 * Whether the tie is strong enough to pull you into that world. The overworld is free;
	 * anywhere else has to be paid for. An unrecognised dimension is treated as gated behind
	 * both, since there is no way to know how far away it really is.
	 */
	public boolean reaches(RegistryKey<World> dimension) {
		if (dimension.equals(World.OVERWORLD)) {
			return true;
		}

		if (dimension.equals(World.NETHER)) {
			return reachesNether();
		}

		if (dimension.equals(World.END)) {
			return reachesEnd();
		}

		return reachesNether() && reachesEnd();
	}

	public SoulBond tiedTo(UUID other, String name) {
		return new SoulBond(Optional.of(other), name, this.pearls, this.netherrack);
	}

	public SoulBond fed(int morePearls, int moreNetherrack) {
		return new SoulBond(this.partner, this.partnerName,
				Math.min(PEARL_COST, this.pearls + morePearls),
				Math.min(NETHERRACK_COST, this.netherrack + moreNetherrack));
	}
}
