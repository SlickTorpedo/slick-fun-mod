package com.slickfun.item;

import java.util.List;

import com.slickfun.util.ToolHost;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Base for the pocket workstations. Each one knows how to open its own screen, which is
 * what both a plain right click and the Swiss Army Knife's quick access call into.
 *
 * <p>The {@link ToolHost} says where the tool is being used from, so a tool with state of
 * its own can write that state back to the right place - the player's hand, or a slot
 * inside a knife.
 */
public abstract class PortableUtilityItem extends Item {
	protected PortableUtilityItem(Settings settings) {
		super(settings);
	}

	/** Opens this utility for the player. */
	public abstract void openFor(ServerPlayerEntity player, ToolHost host);

	/** Translation key suffix used for the tooltip line. */
	protected abstract String tooltipKey();

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			openFor(player, ToolHost.ofHand(player, hand));
		}

		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun." + tooltipKey()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.fits_in_knife").formatted(Formatting.DARK_GRAY));
	}
}
