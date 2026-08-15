package com.slickfun.registry;

import com.slickfun.SlickFunMod;
import com.slickfun.block.DiamondPoopBlock;
import com.slickfun.block.KilnBlock;
import com.slickfun.block.KilnBlockEntity;
import com.slickfun.block.MailBoxBlock;
import com.slickfun.block.MailBoxBlockEntity;
import com.slickfun.block.VacuumChestBlock;
import com.slickfun.block.VacuumChestBlockEntity;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	public static final Block KILN = Registry.register(Registries.BLOCK, SlickFunMod.id("kiln"),
			new KilnBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.STONE_GRAY)
					.strength(3.5F)
					.requiresTool()
					.sounds(BlockSoundGroup.STONE)
					.luminance(state -> state.get(KilnBlock.LIT) ? 13 : 0)));

	public static final BlockEntityType<KilnBlockEntity> KILN_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, SlickFunMod.id("kiln"),
			BlockEntityType.Builder.create(KilnBlockEntity::new, KILN).build(null));

	public static final Block VACUUM_CHEST = Registry.register(Registries.BLOCK, SlickFunMod.id("vacuum_chest"),
			new VacuumChestBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.IRON_GRAY)
					.strength(3.0F)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final BlockEntityType<VacuumChestBlockEntity> VACUUM_CHEST_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, SlickFunMod.id("vacuum_chest"),
			BlockEntityType.Builder.create(VacuumChestBlockEntity::new, VACUUM_CHEST).build(null));

	public static final Block MAIL_BOX = Registry.register(Registries.BLOCK, SlickFunMod.id("mail_box"),
			new MailBoxBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.RED)
					.strength(2.5F)
					.sounds(BlockSoundGroup.WOOD)));

	public static final BlockEntityType<MailBoxBlockEntity> MAIL_BOX_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, SlickFunMod.id("mail_box"),
			BlockEntityType.Builder.create(MailBoxBlockEntity::new, MAIL_BOX).build(null));

	public static final Block DIAMOND_POOP = Registry.register(Registries.BLOCK, SlickFunMod.id("diamond_poop"),
			new DiamondPoopBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.DIAMOND_BLUE)
					.strength(1.0F)
					.sounds(BlockSoundGroup.SLIME)
					.nonOpaque()
					.luminance(state -> 5)));

	private ModBlocks() {
	}

	public static Item createDiamondPoopItem() {
		return new BlockItem(DIAMOND_POOP, new Item.Settings().rarity(net.minecraft.util.Rarity.EPIC));
	}

	/** Registered from {@link ModItems} so it lands in the creative tab with everything else. */
	public static Item createKilnItem() {
		return new BlockItem(KILN, new Item.Settings());
	}

	public static Item createBlockItem(net.minecraft.block.Block block) {
		return new BlockItem(block, new Item.Settings());
	}

	public static void register() {
		SlickFunMod.LOGGER.info("Registered blocks.");
	}
}
