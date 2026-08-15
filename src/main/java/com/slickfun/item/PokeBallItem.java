package com.slickfun.item;

import java.util.List;

import com.slickfun.entity.PokeBallEntity;
import com.slickfun.registry.ModComponents;
import com.slickfun.util.CapturedMob;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Throw at a mob to fold it away; throw the loaded ball to put it back. A way to move
 * animals about without a boat and a lot of patience.
 */
public class PokeBallItem extends Item {
	public PokeBallItem(Settings settings) {
		super(settings);
	}

	public static CapturedMob contentsOf(ItemStack stack) {
		return stack.get(ModComponents.CAPTURED_MOB);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW,
				SoundCategory.PLAYERS, 0.6F, 0.5F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

		if (!world.isClient) {
			PokeBallEntity ball = new PokeBallEntity(world, user, stack);
			ball.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.6F, 1.0F);
			world.spawnEntity(ball);
		}

		// The thrown entity puts a ball back on the ground unless it releases something, so
		// taking it from the hand here is not a loss.
		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}

		user.incrementStat(net.minecraft.stat.Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		CapturedMob held = contentsOf(stack);

		if (held == null) {
			tooltip.add(Text.translatable("tooltip.slickfun.poke_ball.empty").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.poke_ball.hint").formatted(Formatting.DARK_GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.slickfun.poke_ball.holding", held.name().copy().formatted(Formatting.AQUA))
					.formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.poke_ball.release").formatted(Formatting.DARK_GRAY));
		}
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return contentsOf(stack) != null;
	}
}
