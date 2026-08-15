package com.slickfun.item;

import java.util.List;
import java.util.Optional;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/**
 * Points at wherever you last died.
 *
 * <p>Rather than reinventing compass rendering, this writes vanilla's lodestone tracker
 * component, so the client already knows how to draw it - including spinning uselessly when
 * the target is in another dimension.
 */
public class DeathCompassItem extends Item {
	private static final int REFRESH_INTERVAL = 20;

	public DeathCompassItem(Settings settings) {
		super(settings);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient || world.getTime() % REFRESH_INTERVAL != 0 || !(entity instanceof PlayerEntity player)) {
			return;
		}

		Optional<GlobalPos> deathPos = player.getLastDeathPos();
		LodestoneTrackerComponent current = stack.get(DataComponentTypes.LODESTONE_TRACKER);
		Optional<GlobalPos> tracked = current == null ? Optional.empty() : current.target();

		if (!deathPos.equals(tracked)) {
			if (deathPos.isPresent()) {
				stack.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(deathPos, false));
			} else {
				stack.remove(DataComponentTypes.LODESTONE_TRACKER);
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.death_compass").formatted(Formatting.GRAY));

		LodestoneTrackerComponent tracker = stack.get(DataComponentTypes.LODESTONE_TRACKER);

		if (tracker != null && tracker.target().isPresent()) {
			GlobalPos target = tracker.target().get();
			tooltip.add(Text.translatable("tooltip.slickfun.death_compass.target",
					target.pos().getX(), target.pos().getY(), target.pos().getZ()).formatted(Formatting.DARK_GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.slickfun.death_compass.none").formatted(Formatting.DARK_GRAY));
		}
	}
}
