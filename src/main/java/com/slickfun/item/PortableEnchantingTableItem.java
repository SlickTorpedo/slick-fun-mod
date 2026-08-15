package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.screen.PortableEnchantingScreenHandler;

import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A pocket enchanting table. Right click a bookshelf to absorb it and raise the table's
 * power; right click anything else (or use it from the Swiss Army Knife) to open it.
 *
 * <p>Power 0 behaves like a lone enchanting table, power 15 like one ringed by bookshelves.
 */
public class PortableEnchantingTableItem extends PortableUtilityItem {
	public static final int MAX_POWER = 15;

	public PortableEnchantingTableItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "portable_enchanting_table";
	}

	public static int powerOf(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENCHANT_POWER, 0);
	}

	@Override
	public void openFor(ServerPlayerEntity player, com.slickfun.util.ToolHost host) {
		int power = powerOf(host.stack());

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, user) -> new PortableEnchantingScreenHandler(
						syncId, inventory, ScreenHandlerContext.create(user.getWorld(), user.getBlockPos()), power),
				Text.translatable("container.enchant")));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();

		if (!world.getBlockState(pos).isOf(Blocks.BOOKSHELF)) {
			// Falls through to use(), which opens the table.
			return ActionResult.PASS;
		}

		ItemStack stack = context.getStack();
		int power = powerOf(stack);

		if (power >= MAX_POWER) {
			if (!world.isClient && context.getPlayer() != null) {
				context.getPlayer().sendMessage(
						Text.translatable("message.slickfun.enchanting.maxed").formatted(Formatting.GRAY), true);
			}

			return ActionResult.success(world.isClient);
		}

		if (!world.isClient) {
			world.breakBlock(pos, false);
			stack.set(ModComponents.ENCHANT_POWER, power + 1);

			world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.8F, 1.2F);

			if (world instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.ENCHANT,
						pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 40, 0.4D, 0.5D, 0.4D, 0.6D);
			}

			if (context.getPlayer() != null) {
				context.getPlayer().sendMessage(Text.translatable("message.slickfun.enchanting.absorbed",
						power + 1, MAX_POWER).formatted(Formatting.LIGHT_PURPLE), true);
			}
		}

		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("tooltip.slickfun.enchant_power", powerOf(stack), MAX_POWER)
				.formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("tooltip.slickfun.enchant_upgrade").formatted(Formatting.DARK_GRAY));
	}
}
