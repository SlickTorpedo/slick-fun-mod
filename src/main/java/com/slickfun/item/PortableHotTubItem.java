package com.slickfun.item;

import java.util.List;

import com.slickfun.util.HotTubManager;
import com.slickfun.util.ToolHost;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Right click to deploy. Bubbles for three seconds, then boils you alive. Reusable, but on
 * a 30 second cooldown so it is a trip home rather than a teleporter.
 */
public class PortableHotTubItem extends PortableUtilityItem {
	public static final int COOLDOWN_TICKS = 20 * 30;

	public PortableHotTubItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "portable_hot_tub";
	}

	@Override
	public void openFor(ServerPlayerEntity player, ToolHost host) {
		if (player.getItemCooldownManager().isCoolingDown(this) || HotTubManager.isSoaking(player)) {
			return;
		}

		HotTubManager.startSoak(player);
		player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.portable_hot_tub.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.portable_hot_tub.2").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.fits_in_knife").formatted(Formatting.DARK_GRAY));
	}
}
