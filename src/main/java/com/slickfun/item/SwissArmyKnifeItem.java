package com.slickfun.item;

import java.util.List;

import com.slickfun.screen.ToolkitScreenHandler;
import com.slickfun.util.Toolkit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * Holds one of each portable utility and lets you use them straight from the knife.
 *
 * <p>Contents live in the stack's {@code minecraft:container} component, so they survive
 * being dropped, chested, or handed to another player.
 */
public class SwissArmyKnifeItem extends Item {
	public SwissArmyKnifeItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			SimpleInventory contents = new SimpleInventory(Toolkit.SIZE);
			DefaultedList<ItemStack> stored = Toolkit.read(stack);

			for (int slot = 0; slot < Toolkit.SIZE; slot++) {
				contents.setStack(slot, stored.get(slot));
			}

			player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, inventory, ignored) -> new ToolkitScreenHandler(syncId, inventory, contents, hand),
					Text.translatable("container.slickfun.toolkit")));
		}

		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.swiss_army_knife").formatted(Formatting.GRAY));

		DefaultedList<ItemStack> stored = Toolkit.read(stack);
		boolean empty = true;

		for (ItemStack held : stored) {
			if (!held.isEmpty()) {
				empty = false;
				tooltip.add(Text.literal(" - ").formatted(Formatting.DARK_GRAY)
						.append(held.getName().copy().formatted(Formatting.AQUA)));
			}
		}

		if (empty) {
			tooltip.add(Text.translatable("tooltip.slickfun.swiss_army_knife.empty").formatted(Formatting.DARK_GRAY));
		}
	}
}
