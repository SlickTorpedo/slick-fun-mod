package com.slickfun.registry;

import com.google.common.collect.ImmutableSet;
import com.slickfun.SlickFunMod;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

/**
 * Adds the Shulker Trader profession. Its job site is any shulker box, so dropping a spare
 * box next to an unemployed villager is all it takes to hire one.
 */
public final class ModVillagers {
	public static final RegistryKey<PointOfInterestType> SHULKER_BOX_POI_KEY =
			RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, SlickFunMod.id("shulker_box"));

	/** Every shulker box variant counts as a workstation. */
	private static final Block[] SHULKER_BOXES = new Block[] {
			Blocks.SHULKER_BOX,
			Blocks.WHITE_SHULKER_BOX,
			Blocks.ORANGE_SHULKER_BOX,
			Blocks.MAGENTA_SHULKER_BOX,
			Blocks.LIGHT_BLUE_SHULKER_BOX,
			Blocks.YELLOW_SHULKER_BOX,
			Blocks.LIME_SHULKER_BOX,
			Blocks.PINK_SHULKER_BOX,
			Blocks.GRAY_SHULKER_BOX,
			Blocks.LIGHT_GRAY_SHULKER_BOX,
			Blocks.CYAN_SHULKER_BOX,
			Blocks.PURPLE_SHULKER_BOX,
			Blocks.BLUE_SHULKER_BOX,
			Blocks.BROWN_SHULKER_BOX,
			Blocks.GREEN_SHULKER_BOX,
			Blocks.RED_SHULKER_BOX,
			Blocks.BLACK_SHULKER_BOX
	};

	public static PointOfInterestType shulkerBoxPoi;
	public static VillagerProfession shulkerTrader;

	private ModVillagers() {
	}

	public static void register() {
		// ticketCount 1: one villager may claim a given box. searchDistance 1: same as vanilla
		// workstations, so villagers only claim boxes they can actually reach.
		shulkerBoxPoi = PointOfInterestHelper.register(SHULKER_BOX_POI_KEY.getValue(), 1, 1, SHULKER_BOXES);

		shulkerTrader = Registry.register(Registries.VILLAGER_PROFESSION, SlickFunMod.id("shulker_trader"),
				new VillagerProfession(
						"shulker_trader",
						entry -> entry.matchesKey(SHULKER_BOX_POI_KEY),
						entry -> entry.matchesKey(SHULKER_BOX_POI_KEY),
						ImmutableSet.of(),
						ImmutableSet.of(),
						SoundEvents.BLOCK_SHULKER_BOX_OPEN));
	}
}
