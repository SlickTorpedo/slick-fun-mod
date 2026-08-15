package com.slickfun.item;

import java.util.List;

import com.slickfun.util.AdminUtil;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Elytra boost that never runs out. Only does anything while you are actually gliding. */
public class InfiniteFireworkItem extends AdminItem {
	private static final int COOLDOWN_TICKS = 10;

	public InfiniteFireworkItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "infinite_firework";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!AdminUtil.checkAdmin(user)) {
			return TypedActionResult.fail(stack);
		}

		if (!user.isFallFlying()) {
			if (!world.isClient) {
				user.sendMessage(Text.translatable("message.slickfun.not_gliding").formatted(Formatting.GRAY), true);
			}

			return TypedActionResult.fail(stack);
		}

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(3, List.of()));
		world.spawnEntity(new FireworkRocketEntity(world, rocket, user));

		// Never consumes the item - that is the whole point.
		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		return TypedActionResult.success(stack, false);
	}
}
