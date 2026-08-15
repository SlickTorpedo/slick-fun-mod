package com.slickfun.util;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Pair;
import com.slickfun.SlickFunMod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Reads a player's trinket slots, if Trinkets is installed.
 *
 * <p>The mod does not build against Trinkets and does not require it. The two calls needed -
 * {@code TrinketsApi.getTrinketComponent} and {@code TrinketComponent.getAllEquipped} - are
 * looked up once by reflection, and only if Trinkets is actually loaded. One jar therefore
 * works on servers with it and without.
 *
 * <p>Everything that reads this goes through {@link Carried}, so a charm worn in a trinket
 * slot and the same charm loose in a backpack behave identically.
 */
public final class TrinketCompat {
	private static final String TRINKETS = "trinkets";

	private static boolean resolved;
	private static Method getTrinketComponent;
	private static Method getAllEquipped;

	private TrinketCompat() {
	}

	/** Every stack in a trinket slot, or an empty list when Trinkets is not installed. */
	public static List<ItemStack> equippedStacks(PlayerEntity player) {
		if (!resolve()) {
			return List.of();
		}

		try {
			Object optional = getTrinketComponent.invoke(null, player);

			if (!(optional instanceof Optional<?> component) || component.isEmpty()) {
				return List.of();
			}

			Object equipped = getAllEquipped.invoke(component.get());

			if (!(equipped instanceof List<?> entries)) {
				return List.of();
			}

			return entries.stream()
					.filter(entry -> entry instanceof Pair<?, ?>)
					.map(entry -> ((Pair<?, ?>) entry).getSecond())
					.filter(value -> value instanceof ItemStack)
					.map(value -> (ItemStack) value)
					.filter(stack -> !stack.isEmpty())
					.toList();
		} catch (ReflectiveOperationException | ClassCastException e) {
			// Trinkets changed shape under us; stop trying and let the inventory scan cover it.
			SlickFunMod.LOGGER.warn("Trinkets integration disabled: {}", e.toString());
			getTrinketComponent = null;
			getAllEquipped = null;
			return List.of();
		}
	}

	public static boolean available() {
		return resolve();
	}

	private static synchronized boolean resolve() {
		if (!resolved) {
			resolved = true;

			if (FabricLoader.getInstance().isModLoaded(TRINKETS)) {
				try {
					Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
					Class<?> component = Class.forName("dev.emi.trinkets.api.TrinketComponent");
					getTrinketComponent = api.getMethod("getTrinketComponent", LivingEntity.class);
					getAllEquipped = component.getMethod("getAllEquipped");
					SlickFunMod.LOGGER.info("Trinkets found - charms can be worn in trinket slots.");
				} catch (ReflectiveOperationException e) {
					SlickFunMod.LOGGER.warn("Trinkets present but its API did not match: {}", e.toString());
				}
			}
		}

		return getTrinketComponent != null && getAllEquipped != null;
	}
}
