package com.slickfun.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/** Shared bits for the operator-only toys: the tooltip banner and the beam particle trail. */
public abstract class AdminItem extends Item {
	protected AdminItem(Settings settings) {
		super(settings);
	}

	/** Translation key suffix, e.g. {@code tractor_beam} -> {@code tooltip.slickfun.tractor_beam}. */
	protected abstract String tooltipKey();

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun." + tooltipKey()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun.admin_only").formatted(Formatting.DARK_RED, Formatting.ITALIC));
	}

	/** Dots a line of particles from {@code from} to the target entity. */
	protected static void drawBeam(ServerWorld world, Vec3d from, Entity target, ParticleEffect particle) {
		Vec3d to = target.getBoundingBox().getCenter();
		Vec3d delta = to.subtract(from);
		double length = delta.length();

		if (length < 0.01D) {
			return;
		}

		Vec3d step = delta.multiply(1.0D / length).multiply(0.5D);
		int points = (int) Math.min(length * 2.0D, 160.0D);

		for (int i = 1; i <= points; i++) {
			Vec3d point = from.add(step.multiply(i));
			world.spawnParticles(particle, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
	}
}
