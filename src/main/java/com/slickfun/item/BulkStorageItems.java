package com.slickfun.item;

import java.util.List;

import com.slickfun.registry.ModComponents;
import com.slickfun.util.BulkStore;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Containers that hold one kind of thing and an absurd amount of it.
 *
 * <p>Both work the same way round: crouch and click to swallow everything matching out of your
 * pack, click to get something back. Holding a single kind is what lets the count be one number
 * instead of an inventory, which is what makes the capacity silly.
 */
public final class BulkStorageItems {
	private BulkStorageItems() {
	}

	/** Shared loading, counting and tooltip work. */
	public abstract static class Bulk extends Item {
		protected Bulk(Settings settings) {
			super(settings);
		}

		/** The most it will ever hold. */
		protected abstract int capacity();

		/** Whether this container will take that kind of item at all. */
		protected abstract boolean storable(ItemStack candidate);

		/** What a plain right click does once there is something inside. */
		protected abstract TypedActionResult<ItemStack> onUse(ServerWorld level, ServerPlayerEntity player,
				ItemStack held, BulkStore store);

		protected abstract String emptyMessageKey();

		/** Whether a plain click on an empty container should be refused. */
		protected boolean requiresContents() {
			return true;
		}

		/** The tooltip line describing how to work it. */
		protected String actionKey() {
			return "tooltip.slickfun.bulk.load";
		}

		public static BulkStore storeOf(ItemStack stack) {
			return stack.getOrDefault(ModComponents.BULK_STORE, BulkStore.EMPTY);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack held = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(held, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld level)) {
				return TypedActionResult.fail(held);
			}

			if (player.isSneaking()) {
				return load(level, player, held);
			}

			BulkStore store = storeOf(held);

			if (store.isEmpty() && requiresContents()) {
				player.sendMessage(Text.translatable(emptyMessageKey()).formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(held);
			}

			return onUse(level, player, held, store);
		}

		/**
		 * Pulls every matching item out of the player's pack.
		 *
		 * <p>An empty container locks onto the first thing it finds and takes only that from
		 * then on, which is what stops a mixed pack turning into an unusable jumble.
		 */
		private TypedActionResult<ItemStack> load(ServerWorld level, ServerPlayerEntity player, ItemStack held) {
			BulkStore store = storeOf(held);
			int room = capacity() - store.count();

			if (room <= 0) {
				player.sendMessage(Text.translatable("message.slickfun.bulk.full").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(held);
			}

			ItemStack lockedTo = store.sample();
			int taken = 0;

			for (int slot = 0; slot < player.getInventory().size() && taken < room; slot++) {
				ItemStack candidate = player.getInventory().getStack(slot);

				// Never swallow the container itself, or another one like it.
				if (candidate.isEmpty() || candidate == held || candidate.getItem() instanceof Bulk) {
					continue;
				}

				if (!storable(candidate)) {
					continue;
				}

				if (!lockedTo.isEmpty() && !ItemStack.areItemsAndComponentsEqual(lockedTo, candidate)) {
					continue;
				}

				if (lockedTo.isEmpty()) {
					lockedTo = candidate.copyWithCount(1);
				}

				int moved = Math.min(room - taken, candidate.getCount());
				candidate.decrement(moved);
				taken += moved;

				if (candidate.isEmpty()) {
					player.getInventory().setStack(slot, ItemStack.EMPTY);
				}
			}

			if (taken == 0) {
				player.sendMessage(Text.translatable("message.slickfun.bulk.nothing").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(held);
			}

			BulkStore loaded = store.with(lockedTo, taken, capacity());
			held.set(ModComponents.BULK_STORE, loaded);

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.6F);
			player.sendMessage(Text.translatable("message.slickfun.bulk.loaded",
					taken, loaded.sample().getName(), loaded.count()).formatted(Formatting.AQUA), true);

			return TypedActionResult.success(held);
		}

		protected static void save(ItemStack held, BulkStore store) {
			if (store.isEmpty()) {
				held.remove(ModComponents.BULK_STORE);
			} else {
				held.set(ModComponents.BULK_STORE, store);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			BulkStore store = storeOf(stack);

			if (store.isEmpty()) {
				tooltip.add(Text.translatable("tooltip.slickfun.bulk.empty").formatted(Formatting.DARK_GRAY));
			} else {
				tooltip.add(Text.translatable("tooltip.slickfun.bulk.holding",
						store.count(), store.sample().getName()).formatted(Formatting.AQUA));
			}

			tooltip.add(Text.translatable(actionKey()).formatted(Formatting.DARK_GRAY));
		}
	}

	/**
	 * One rocket in your hand, thousands in the tube.
	 *
	 * <p>While gliding it boosts you exactly as a held rocket would, because it hands the stored
	 * stack to the same entity vanilla uses; on the ground it launches one properly instead.
	 */
	public static class InfiniteRocket extends Bulk {
		/** Effectively no limit. Kept short of overflow rather than truly unbounded. */
		private static final int CAPACITY = 1_000_000_000;

		public InfiniteRocket(Settings settings) {
			super(settings);
		}

		@Override
		protected int capacity() {
			return CAPACITY;
		}

		@Override
		protected boolean storable(ItemStack candidate) {
			return candidate.isOf(Items.FIREWORK_ROCKET);
		}

		@Override
		protected String emptyMessageKey() {
			return "message.slickfun.rocket.empty";
		}

		@Override
		protected TypedActionResult<ItemStack> onUse(ServerWorld level, ServerPlayerEntity player,
				ItemStack held, BulkStore store) {
			ItemStack rocket = store.one();

			// The same stack vanilla would have consumed, so flight duration and effects carry.
			FireworkRocketEntity firework = player.isFallFlying()
					? new FireworkRocketEntity(level, rocket, player)
					: new FireworkRocketEntity(level, rocket, player.getX(), player.getY(), player.getZ(), true);

			level.spawnEntity(firework);
			save(held, store.less(1));

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0F, 1.0F);

			return TypedActionResult.success(held);
		}
	}

	/**
	 * Twenty double chests of one thing, in one slot.
	 *
	 * <p>The one-kind rule is the whole trick: a single item type and a single number needs no
	 * inventory behind it, so the capacity can be preposterous without costing anything.
	 */
	public static class InsanelyLargeStorage extends Bulk {
		private static final int DOUBLE_CHESTS = 20;
		private static final int SLOTS_PER_DOUBLE_CHEST = 54;

		/** 20 x 54 x 64. */
		public static final int CAPACITY = DOUBLE_CHESTS * SLOTS_PER_DOUBLE_CHEST * 64;

		public InsanelyLargeStorage(Settings settings) {
			super(settings);
		}

		/** Fed ender pearls, so it is capable of collecting. Permanent once crafted. */
		public static boolean isAutomatic(ItemStack stack) {
			return stack.getOrDefault(ModComponents.AUTO_STORE, false);
		}

		/**
		 * Whether it is collecting right now.
		 *
		 * <p>An upgraded container defaults to on, and switches itself off the moment you take
		 * something out - otherwise it snatches it straight back and the contents are unreachable.
		 */
		public static boolean isCollecting(ItemStack stack) {
			return isAutomatic(stack) && stack.getOrDefault(ModComponents.AUTO_ACTIVE, true);
		}

		public static void setCollecting(ItemStack stack, boolean on) {
			stack.set(ModComponents.AUTO_ACTIVE, on);
		}

		/** An empty one still opens - that is how you put the first thing in. */
		@Override
		protected boolean requiresContents() {
			return false;
		}

		@Override
		protected String actionKey() {
			return "tooltip.slickfun.bulk.open";
		}

		@Override
		protected int capacity() {
			return CAPACITY;
		}

		@Override
		protected boolean storable(ItemStack candidate) {
			// Anything except another container of this kind, which Bulk already excludes.
			return true;
		}

		@Override
		protected String emptyMessageKey() {
			return "message.slickfun.bulk.is_empty";
		}

		/** Opens it like a chest. Reaching in is what people expect of storage. */
		@Override
		protected TypedActionResult<ItemStack> onUse(ServerWorld level, ServerPlayerEntity player,
				ItemStack held, BulkStore store) {
			com.slickfun.screen.BulkStorageScreenHandler.open(player, player.getActiveHand(), CAPACITY);
			return TypedActionResult.consume(held);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			super.appendTooltip(stack, context, tooltip, type);
			tooltip.add(Text.translatable("tooltip.slickfun.bulk.capacity", CAPACITY, DOUBLE_CHESTS)
					.formatted(Formatting.DARK_GRAY));

			// The upgrade recipe is a coded one, so it never appears in the recipe book.
			// Without this line there is nothing anywhere that tells you it exists.
			tooltip.add(Text.translatable(isAutomatic(stack)
							? "tooltip.slickfun.bulk.automatic"
							: "tooltip.slickfun.bulk.upgrade")
					.formatted(isAutomatic(stack) ? Formatting.LIGHT_PURPLE : Formatting.DARK_GRAY));
		}
	}
}
