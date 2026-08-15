package com.slickfun.util;

import com.slickfun.item.SwissArmyKnifeItem;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;

/**
 * Where a portable tool is being used from.
 *
 * <p>Tools with no state of their own don't care, but a portable furnace does: it has to
 * write its burn counters back to the right stack, and that stack might be sitting inside a
 * Swiss Army Knife rather than in the player's hand.
 */
public interface ToolHost {
	ItemStack stack();

	/** Persist any changes made to {@link #stack()}. */
	void markChanged();

	/** False once the holder has gone - a different item in hand, or a dropped knife. */
	boolean isValid();

	static ToolHost ofHand(PlayerEntity player, Hand hand) {
		return new HandHost(player, hand);
	}

	static ToolHost inKnife(PlayerEntity player, Hand hand, int slot) {
		return new KnifeHost(player, hand, slot);
	}

	/** Held directly: mutating the stack is already persistent. */
	record HandHost(PlayerEntity player, Hand hand) implements ToolHost {
		@Override
		public ItemStack stack() {
			return player.getStackInHand(hand);
		}

		@Override
		public void markChanged() {
		}

		@Override
		public boolean isValid() {
			return !stack().isEmpty();
		}
	}

	/** Nested in a knife: the tool is a detached stack that must be written back on change. */
	final class KnifeHost implements ToolHost {
		private final PlayerEntity player;
		private final Hand hand;
		private final int slot;
		private final ItemStack tool;

		KnifeHost(PlayerEntity player, Hand hand, int slot) {
			this.player = player;
			this.hand = hand;
			this.slot = slot;
			this.tool = Toolkit.read(player.getStackInHand(hand)).get(slot);
		}

		@Override
		public ItemStack stack() {
			return this.tool;
		}

		@Override
		public void markChanged() {
			ItemStack knife = this.player.getStackInHand(this.hand);

			if (!(knife.getItem() instanceof SwissArmyKnifeItem)) {
				return;
			}

			DefaultedList<ItemStack> stored = Toolkit.read(knife);
			stored.set(this.slot, this.tool);
			Toolkit.writeAll(knife, stored);
		}

		@Override
		public boolean isValid() {
			return this.player.getStackInHand(this.hand).getItem() instanceof SwissArmyKnifeItem;
		}
	}
}
