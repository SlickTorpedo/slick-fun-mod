package com.slickfun.item;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Creepers treat you like a cat; cats stop treating you like a threat.
 *
 * <p>Wearable in a Trinkets necklace slot when Trinkets is installed, and otherwise just
 * works from your inventory - the tooltip says which, so nobody has to guess.
 */
public class CatCollarItem extends Item {
	public CatCollarItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.cat_collar.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.cat_collar.2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(FabricLoader.getInstance().isModLoaded("trinkets")
						? "tooltip.slickfun.cat_collar.trinket"
						: "tooltip.slickfun.cat_collar.inventory")
				.formatted(Formatting.DARK_GRAY));
	}
}
