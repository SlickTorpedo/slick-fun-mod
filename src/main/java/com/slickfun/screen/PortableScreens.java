package com.slickfun.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Vanilla workstation screens, opened without the matching block being present.
 *
 * <p>Each handler is a thin subclass whose only job is to stop {@code canUse} from checking
 * that the player is still standing next to the block. The screen handler types stay
 * vanilla, so the client renders them with no client-side code.
 *
 * <p>The context is bound to the player's own position rather than {@link
 * ScreenHandlerContext#EMPTY}, because several handlers route real work through it - recipe
 * matching, dropping the grid on close, the anvil's clang. An empty context no-ops all of it.
 */
public final class PortableScreens {
	private PortableScreens() {
	}

	private static ScreenHandlerContext contextAt(PlayerEntity player) {
		return ScreenHandlerContext.create(player.getWorld(), player.getBlockPos());
	}

	private static NamedScreenHandlerFactory factory(Text title, ScreenHandlerFactory inner) {
		return new SimpleNamedScreenHandlerFactory(inner, title);
	}

	public static void openCrafting(ServerPlayerEntity player) {
		player.openHandledScreen(factory(Text.translatable("container.crafting"),
				(syncId, inventory, user) -> new PortableCrafting(syncId, inventory, contextAt(user))));
	}

	public static void openAnvil(ServerPlayerEntity player) {
		player.openHandledScreen(factory(Text.translatable("container.repair"),
				(syncId, inventory, user) -> new PortableAnvil(syncId, inventory, contextAt(user))));
	}

	public static void openSmithing(ServerPlayerEntity player) {
		player.openHandledScreen(factory(Text.translatable("container.upgrade"),
				(syncId, inventory, user) -> new PortableSmithing(syncId, inventory, contextAt(user))));
	}

	public static void openGrindstone(ServerPlayerEntity player) {
		player.openHandledScreen(factory(Text.translatable("container.grindstone_title"),
				(syncId, inventory, user) -> new PortableGrindstone(syncId, inventory, contextAt(user))));
	}

	public static void openStonecutter(ServerPlayerEntity player) {
		player.openHandledScreen(factory(Text.translatable("container.stonecutter"),
				(syncId, inventory, user) -> new PortableStonecutter(syncId, inventory, contextAt(user))));
	}

	/**
	 * Opens the player's real ender chest. {@link net.minecraft.inventory.EnderChestInventory}
	 * only delegates {@code canPlayerUse} when a block entity is attached, so with none set
	 * it is always usable.
	 */
	public static void openEnderChest(ServerPlayerEntity player) {
		player.openHandledScreen(factory(Text.translatable("container.enderchest"),
				(syncId, inventory, user) -> GenericContainerScreenHandler.createGeneric9x3(
						syncId, inventory, user.getEnderChestInventory())));
	}

	/**
	 * A bin backed by a throwaway inventory. Nothing writes it anywhere, so whatever is left
	 * inside when the screen closes simply ceases to exist.
	 */
	public static void openTrashCan(ServerPlayerEntity player) {
		SimpleInventory bin = new SimpleInventory(27);

		player.openHandledScreen(factory(Text.translatable("container.slickfun.trash_can"),
				(syncId, inventory, user) -> new TrashCan(syncId, inventory, bin)));
	}

	private static class PortableCrafting extends CraftingScreenHandler {
		PortableCrafting(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
			super(syncId, playerInventory, context);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static class PortableAnvil extends AnvilScreenHandler {
		PortableAnvil(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
			super(syncId, playerInventory, context);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static class PortableSmithing extends SmithingScreenHandler {
		PortableSmithing(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
			super(syncId, playerInventory, context);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static class PortableGrindstone extends GrindstoneScreenHandler {
		PortableGrindstone(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
			super(syncId, playerInventory, context);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static class PortableStonecutter extends StonecutterScreenHandler {
		PortableStonecutter(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
			super(syncId, playerInventory, context);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static class TrashCan extends GenericContainerScreenHandler {
		private final Inventory bin;

		TrashCan(int syncId, PlayerInventory playerInventory, Inventory bin) {
			super(net.minecraft.screen.ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, bin, 3);
			this.bin = bin;
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}

		@Override
		public void onClosed(PlayerEntity player) {
			int destroyed = 0;

			for (int slot = 0; slot < this.bin.size(); slot++) {
				if (!this.bin.getStack(slot).isEmpty()) {
					destroyed++;
				}
			}

			this.bin.clear();
			super.onClosed(player);

			if (destroyed > 0) {
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 0.6F, 1.4F);
				player.sendMessage(Text.translatable("message.slickfun.trash_can.emptied", destroyed)
						.formatted(Formatting.GRAY), true);
			}
		}

		@Override
		public ItemStack quickMove(PlayerEntity player, int slot) {
			// Shift-clicking into a bin should work normally; vanilla's implementation is fine.
			return super.quickMove(player, slot);
		}
	}
}
