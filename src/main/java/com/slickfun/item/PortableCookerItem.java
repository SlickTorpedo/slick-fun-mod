package com.slickfun.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.slickfun.registry.ModComponents;
import com.slickfun.screen.PortableCookerScreenHandler;
import com.slickfun.util.CookState;
import com.slickfun.util.ToolHost;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;

/**
 * A furnace, smoker or kiln you carry. The three slots and the burn counters live on the item
 * stack, so a half-finished smelt survives being put in a chest, handed to someone else, or
 * tucked into a Swiss Army Knife.
 */
public class PortableCookerItem extends PortableUtilityItem {
	private static final int SLOTS = 3;

	private final CookerType type;

	public PortableCookerItem(Settings settings, CookerType type) {
		super(settings);
		this.type = type;
	}

	public CookerType type() {
		return this.type;
	}

	@Override
	protected String tooltipKey() {
		return this.type.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public void openFor(ServerPlayerEntity player, ToolHost host) {
		ItemStack stack = host.stack();

		SimpleInventory contents = PortableCookerScreenHandler.newInventory();
		DefaultedList<ItemStack> stored = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
		stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(stored);

		for (int slot = 0; slot < SLOTS; slot++) {
			contents.setStack(slot, stored.get(slot));
		}

		CookState state = stack.getOrDefault(ModComponents.COOK_STATE, CookState.EMPTY);
		ArrayPropertyDelegate properties = PortableCookerScreenHandler.newProperties();
		properties.set(0, state.burnTime());
		properties.set(1, Math.max(state.fuelTime(), state.burnTime()));
		properties.set(2, state.cookTime());
		properties.set(3, 200);

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, ignored) ->
						new PortableCookerScreenHandler(this.type, syncId, inventory, contents, properties, host),
				Text.translatable(this.type.translationKey())));
	}

	/** Writes the live screen state back onto the item. */
	public static void save(ItemStack cooker, Inventory contents, CookState state) {
		List<ItemStack> stacks = new ArrayList<>(SLOTS);

		for (int slot = 0; slot < SLOTS; slot++) {
			stacks.add(contents.getStack(slot).copy());
		}

		cooker.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
		cooker.set(ModComponents.COOK_STATE, state);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);

		DefaultedList<ItemStack> stored = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
		stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(stored);

		for (ItemStack held : stored) {
			if (!held.isEmpty()) {
				tooltip.add(Text.literal(" - ").formatted(Formatting.DARK_GRAY)
						.append(held.getName().copy().formatted(Formatting.AQUA))
						.append(Text.literal(" x" + held.getCount()).formatted(Formatting.DARK_GRAY)));
			}
		}

		if (stack.getOrDefault(ModComponents.COOK_STATE, CookState.EMPTY).isBurning()) {
			tooltip.add(Text.translatable("tooltip.slickfun.cooker.lit").formatted(Formatting.GOLD));
		}

		tooltip.add(Text.translatable("tooltip.slickfun.cooker.paused").formatted(Formatting.DARK_GRAY));
	}
}
