package com.slickfun.registry;

import com.slickfun.SlickFunMod;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public final class ModItemGroups {
	public static final RegistryKey<ItemGroup> SLICK_FUN = RegistryKey.of(RegistryKeys.ITEM_GROUP, SlickFunMod.id("general"));

	private ModItemGroups() {
	}

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, SLICK_FUN, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.PORTABLE_HOT_TUB))
				.displayName(Text.translatable("itemGroup.slickfun.general"))
				.entries((context, entries) -> ModItems.ALL.forEach(entries::add))
				.build());
	}
}
