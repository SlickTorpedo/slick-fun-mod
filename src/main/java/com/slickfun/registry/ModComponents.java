package com.slickfun.registry;

import com.mojang.serialization.Codec;
import com.slickfun.SlickFunMod;
import com.slickfun.util.CapturedMob;
import com.slickfun.util.CookState;

import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModComponents {
	/**
	 * Bookshelf-equivalent power stored on a Portable Enchanting Table. 0 behaves like a
	 * lone enchanting table; 15 is the same as one ringed by bookshelves.
	 */
	public static final ComponentType<Integer> ENCHANT_POWER = register("enchant_power",
			ComponentType.<Integer>builder()
					.codec(Codec.intRange(0, 15))
					.packetCodec(PacketCodecs.VAR_INT)
					.build());

	/** Whether an Item Magnet is switched on. */
	public static final ComponentType<Boolean> MAGNET_ACTIVE = register("magnet_active",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** The mob folded up inside a Poke Ball. */
	public static final ComponentType<CapturedMob> CAPTURED_MOB = register("captured_mob",
			ComponentType.<CapturedMob>builder()
					.codec(CapturedMob.CODEC)
					.packetCodec(CapturedMob.PACKET_CODEC)
					.build());

	/** The herd folded up inside a Leash Anchor. Same storage as the Poke Ball, but a list. */
	public static final ComponentType<java.util.List<CapturedMob>> LEASHED_HERD = register("leashed_herd",
			ComponentType.<java.util.List<CapturedMob>>builder()
					.codec(CapturedMob.CODEC.listOf())
					.packetCodec(CapturedMob.PACKET_CODEC.collect(PacketCodecs.toList()))
					.build());

	/** Who a Soul Tie is tied to, and how far it can reach. */
	public static final ComponentType<com.slickfun.util.SoulBond> SOUL_BOND = register("soul_bond",
			ComponentType.<com.slickfun.util.SoulBond>builder()
					.codec(com.slickfun.util.SoulBond.CODEC)
					.packetCodec(com.slickfun.util.SoulBond.PACKET_CODEC)
					.build());

	/** How far an Admin Magnet reaches, cycled by its owner. */
	public static final ComponentType<Integer> MAGNET_RANGE = register("magnet_range",
			ComponentType.<Integer>builder()
					.codec(Codec.intRange(10, 100))
					.packetCodec(PacketCodecs.VAR_INT)
					.build());

	/** Ender eyes fed to a Real Self Destruct Button, on its way to being all-seeing. */
	public static final ComponentType<Integer> EYE_CHARGE = register("eye_charge",
			ComponentType.<Integer>builder()
					.codec(Codec.intRange(0, 64))
					.packetCodec(PacketCodecs.VAR_INT)
					.build());

	/** One item type and a very large count, for the bulk containers. */
	public static final ComponentType<com.slickfun.util.BulkStore> BULK_STORE = register("bulk_store",
			ComponentType.<com.slickfun.util.BulkStore>builder()
					.codec(com.slickfun.util.BulkStore.CODEC)
					.packetCodec(com.slickfun.util.BulkStore.PACKET_CODEC)
					.build());

	/**
	 * Whether an upgraded container is currently collecting. Separate from the upgrade itself
	 * so switching it off is temporary and never wastes the pearls.
	 */
	public static final ComponentType<Boolean> AUTO_ACTIVE = register("auto_active",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** Whether a bulk container has been fed ender pearls and collects on its own. */
	public static final ComponentType<Boolean> AUTO_STORE = register("auto_store",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** Whether a Mob Repellent is switched on. */
	public static final ComponentType<Boolean> REPELLENT_ACTIVE = register("repellent_active",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** A shield carrying one free block from the Shield Charger. */
	public static final ComponentType<Boolean> SHIELD_CHARGED = register("shield_charged",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** The block an Ore Compass is hunting for. */
	public static final ComponentType<net.minecraft.util.Identifier> TARGET_BLOCK = register("target_block",
			ComponentType.<net.minecraft.util.Identifier>builder()
					.codec(net.minecraft.util.Identifier.CODEC)
					.packetCodec(net.minecraft.util.Identifier.PACKET_CODEC)
					.build());

	/** The biome a Biome Compass is hunting for. */
	public static final ComponentType<net.minecraft.util.Identifier> TARGET_BIOME = register("target_biome",
			ComponentType.<net.minecraft.util.Identifier>builder()
					.codec(net.minecraft.util.Identifier.CODEC)
					.packetCodec(net.minecraft.util.Identifier.PACKET_CODEC)
					.build());

	/** Ender pearls fed into an Item Finder on its way to becoming the ender one. */
	public static final ComponentType<Integer> PEARL_CHARGE = register("pearl_charge",
			ComponentType.<Integer>builder()
					.codec(Codec.intRange(0, 128))
					.packetCodec(PacketCodecs.VAR_INT)
					.build());

	/** The channel a TV Remote is currently set to. Purely decorative. */
	public static final ComponentType<Integer> TV_CHANNEL = register("tv_channel",
			ComponentType.<Integer>builder()
					.codec(Codec.intRange(1, 99))
					.packetCodec(PacketCodecs.VAR_INT)
					.build());

	/** Whether the Really Dark Shades are on. */
	public static final ComponentType<Boolean> SHADES_WORN = register("shades_worn",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** Whether a Boombox is mid-track, so a second right click stops it. */
	public static final ComponentType<Boolean> BOOMBOX_PLAYING = register("boombox_playing",
			ComponentType.<Boolean>builder()
					.codec(Codec.BOOL)
					.packetCodec(PacketCodecs.BOOL)
					.build());

	/** Burn and cook counters for a portable furnace, smoker or kiln. */
	public static final ComponentType<CookState> COOK_STATE = register("cook_state",
			ComponentType.<CookState>builder()
					.codec(CookState.CODEC)
					.packetCodec(CookState.PACKET_CODEC)
					.build());

	private ModComponents() {
	}

	private static <T> ComponentType<T> register(String path, ComponentType<T> type) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, SlickFunMod.id(path), type);
	}

	public static void register() {
		SlickFunMod.LOGGER.info("Registered data components.");
	}
}
