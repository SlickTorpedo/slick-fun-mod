package com.slickfun.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.slickfun.SlickFunMod;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;

/**
 * The armour material behind the Bubble Suit.
 *
 * <p>Zero protection on every slot, and that is the joke - it is armour in every visible
 * respect, right down to sitting in the chest slot and rendering over your skin, and it stops
 * exactly nothing.
 */
public final class ModArmorMaterials {
	public static final RegistryEntry<ArmorMaterial> BUBBLE = createBubble();

	private ModArmorMaterials() {
	}

	private static RegistryEntry<ArmorMaterial> createBubble() {
		Map<ArmorItem.Type, Integer> defence = new EnumMap<>(ArmorItem.Type.class);

		for (ArmorItem.Type type : ArmorItem.Type.values()) {
			defence.put(type, 0);
		}

		return Registry.registerReference(Registries.ARMOR_MATERIAL, SlickFunMod.id("bubble"),
				new ArmorMaterial(
						defence,
						0,
						SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
						() -> Ingredient.ofItems(net.minecraft.item.Items.GLASS),
						List.of(new ArmorMaterial.Layer(SlickFunMod.id("bubble"))),
						0.0F,
						0.0F));
	}

	/** Forces class initialisation so the material exists before any item asks for it. */
	public static void register() {
		SlickFunMod.LOGGER.info("Registered armour materials.");
	}
}
