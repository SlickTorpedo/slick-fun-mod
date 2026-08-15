package com.slickfun.screen;

import java.util.List;
import java.util.Optional;

import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * A vanilla enchanting table whose bookshelf power comes from the item instead of the blocks
 * around it.
 *
 * <p>Only {@link #onContentChanged} is replaced - the part that counts nearby bookshelves.
 * Everything else, including {@code onButtonClick} which actually applies the enchantments,
 * is untouched vanilla. That matters for two reasons: the offers you see are exactly the
 * offers you get, and the enchantment pool is still whatever is in the
 * {@code #minecraft:in_enchanting_table} tag - so any custom enchantments a datapack or
 * another mod adds to that tag show up here with no extra work.
 *
 * <p>Vanilla keeps {@code inventory}, {@code random} and {@code seed} private, so this class
 * reaches the same state through public API: the item comes from slot 0, the seed from
 * {@link #getSeed()}, and the RNG is a private instance seeded identically. Since vanilla
 * re-seeds from {@code seed} before every roll, the sequences line up exactly.
 */
public class PortableEnchantingScreenHandler extends EnchantmentScreenHandler {
	private final ScreenHandlerContext context;
	private final Inventory enchantInventory;
	private final Random previewRandom = Random.create();
	private final int bonusPower;

	public PortableEnchantingScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context, int bonusPower) {
		super(syncId, playerInventory, context);
		this.context = context;
		this.bonusPower = bonusPower;
		this.enchantInventory = this.slots.get(0).inventory;
	}

	@Override
	public void onContentChanged(Inventory inventory) {
		if (inventory != this.enchantInventory) {
			return;
		}

		ItemStack stack = inventory.getStack(0);

		if (stack.isEmpty() || !stack.isEnchantable()) {
			for (int i = 0; i < 3; i++) {
				this.enchantmentPower[i] = 0;
				this.enchantmentId[i] = -1;
				this.enchantmentLevel[i] = -1;
			}

			return;
		}

		this.context.run((world, pos) -> {
			IndexedIterable<RegistryEntry<Enchantment>> indexed =
					world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getIndexedEntries();
			int power = totalPower(world, pos);

			this.previewRandom.setSeed(this.getSeed());

			for (int i = 0; i < 3; i++) {
				this.enchantmentPower[i] = EnchantmentHelper.calculateRequiredExperienceLevel(this.previewRandom, i, power, stack);
				this.enchantmentId[i] = -1;
				this.enchantmentLevel[i] = -1;

				if (this.enchantmentPower[i] < i + 1) {
					this.enchantmentPower[i] = 0;
				}
			}

			for (int i = 0; i < 3; i++) {
				if (this.enchantmentPower[i] > 0) {
					List<EnchantmentLevelEntry> offers = generateEnchantments(world.getRegistryManager(), stack, i, this.enchantmentPower[i]);

					if (!offers.isEmpty()) {
						EnchantmentLevelEntry offer = offers.get(this.previewRandom.nextInt(offers.size()));
						this.enchantmentId[i] = indexed.getRawId(offer.enchantment);
						this.enchantmentLevel[i] = offer.level;
					}
				}
			}

			this.sendContentUpdates();
		});
	}

	/** The item's stored power, plus any real bookshelves you happen to be standing among. */
	private int totalPower(World world, BlockPos pos) {
		int nearby = 0;

		for (BlockPos offset : EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
			if (EnchantingTableBlock.canAccessPowerProvider(world, pos, offset)) {
				nearby++;
			}
		}

		return Math.min(15, nearby + this.bonusPower);
	}

	/** Mirrors the private vanilla method of the same name so previews match what you get. */
	private List<EnchantmentLevelEntry> generateEnchantments(DynamicRegistryManager registryManager, ItemStack stack, int slot, int level) {
		this.previewRandom.setSeed(this.getSeed() + slot);

		Optional<RegistryEntryList.Named<Enchantment>> pool =
				registryManager.get(RegistryKeys.ENCHANTMENT).getEntryList(EnchantmentTags.IN_ENCHANTING_TABLE);

		if (pool.isEmpty()) {
			return List.of();
		}

		List<EnchantmentLevelEntry> offers = EnchantmentHelper.generateEnchantments(this.previewRandom, stack, level, pool.get().stream());

		if (stack.isOf(Items.BOOK) && offers.size() > 1) {
			offers.remove(this.previewRandom.nextInt(offers.size()));
		}

		return offers;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
