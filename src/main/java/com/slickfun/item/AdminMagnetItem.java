package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.screen.MagnetTargetScreenHandler;
import com.slickfun.util.AdminUtil;
import com.slickfun.util.SignPrompt;
import com.slickfun.util.MagnetSweep;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Type what you want and it comes to you, out of every chest, pack and shulker in range.
 *
 * <p>Names of players and mobs work too - those get dragged bodily rather than emptied.
 */
public class AdminMagnetItem extends AdminItem {
	public AdminMagnetItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "admin_magnet";
	}

	public static int rangeOf(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MAGNET_RANGE, MagnetSweep.MIN_RANGE);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		if (!AdminUtil.checkAdmin(user) || !(user instanceof ServerPlayerEntity admin)) {
			return TypedActionResult.fail(stack);
		}

		if (admin.isSneaking()) {
			return cycleRange(admin, stack);
		}

		int range = rangeOf(stack);
		admin.sendMessage(Text.translatable("message.slickfun.magnet.prompt", range).formatted(Formatting.GRAY), true);

		SignPrompt.ask(admin, query -> {
			if (query.isBlank() || admin.isRemoved()) {
				return;
			}

			MagnetTargetScreenHandler.open(admin, query, range);
		});

		return TypedActionResult.consume(stack);
	}

	private static TypedActionResult<ItemStack> cycleRange(ServerPlayerEntity admin, ItemStack stack) {
		int next = rangeOf(stack) + MagnetSweep.RANGE_STEP;

		if (next > MagnetSweep.MAX_RANGE) {
			next = MagnetSweep.MIN_RANGE;
		}

		stack.set(ModComponents.MAGNET_RANGE, next);
		admin.getServerWorld().playSound(null, admin.getX(), admin.getY(), admin.getZ(),
				SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6F, 0.8F + next / 100.0F);
		admin.sendMessage(Text.translatable("message.slickfun.magnet.range", next).formatted(Formatting.AQUA), true);

		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun.admin_magnet.range", rangeOf(stack)).formatted(Formatting.AQUA));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
