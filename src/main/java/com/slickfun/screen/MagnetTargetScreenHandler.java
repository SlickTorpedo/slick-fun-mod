package com.slickfun.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.slickfun.util.ServerScheduler;
import com.slickfun.util.MagnetSweep;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * What the Admin Magnet found for your query: items, mob types and online players together.
 *
 * <p>Clicking one runs the sweep. Nothing here can be picked up - the icons are stand-ins for
 * a target, not the target itself.
 */
public class MagnetTargetScreenHandler extends GenericContainerScreenHandler {
	private static final int SLOTS = 54;

	/** Exactly one of the three is set. */
	public record Target(Item item, EntityType<?> entityType, String playerName) {
	}

	private final SimpleInventory icons;
	private final List<Target> targets;
	private final int range;

	private MagnetTargetScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory icons,
			List<Target> targets, int range) {
		super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, icons, 6);
		this.icons = icons;
		this.targets = targets;
		this.range = range;
	}

	public static void open(ServerPlayerEntity admin, String query, int range) {
		List<Target> targets = resolve(admin, query);

		if (targets.isEmpty()) {
			admin.sendMessage(Text.translatable("message.slickfun.magnet.no_match", query).formatted(Formatting.GRAY), false);
			return;
		}

		SimpleInventory icons = new SimpleInventory(SLOTS);
		List<Target> shown = targets.size() > SLOTS ? targets.subList(0, SLOTS) : targets;

		for (int slot = 0; slot < shown.size(); slot++) {
			icons.setStack(slot, iconFor(shown.get(slot)));
		}

		List<Target> chosen = List.copyOf(shown);

		admin.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, ignored) -> new MagnetTargetScreenHandler(syncId, inventory, icons, chosen, range),
				Text.translatable("container.slickfun.magnet_results", query, range)));
	}

	/** Players first, then mobs, then items - the narrower the match, the higher it sits. */
	private static List<Target> resolve(ServerPlayerEntity admin, String query) {
		String needle = query.toLowerCase(Locale.ROOT).trim();
		List<Target> found = new ArrayList<>();

		if (needle.isEmpty()) {
			return found;
		}

		for (ServerPlayerEntity player : admin.getServer().getPlayerManager().getPlayerList()) {
			if (player.getGameProfile().getName().toLowerCase(Locale.ROOT).contains(needle)) {
				found.add(new Target(null, null, player.getGameProfile().getName()));
			}
		}

		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			if (type == EntityType.PLAYER) {
				continue;
			}

			if (type.getName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
				found.add(new Target(null, type, null));
			}
		}

		for (Item item : Registries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}

			if (new ItemStack(item).getName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
				found.add(new Target(item, null, null));
			}
		}

		return found;
	}

	private static ItemStack iconFor(Target target) {
		if (target.item() != null) {
			return new ItemStack(target.item());
		}

		if (target.playerName() != null) {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponentTypes.CUSTOM_NAME,
					Text.literal(target.playerName()).formatted(Formatting.YELLOW));
			return head;
		}

		// A spawn egg reads as "this mob" instantly; not every mob has one, so fall back.
		SpawnEggItem egg = SpawnEggItem.forEntity(target.entityType());
		ItemStack icon = new ItemStack(egg == null ? Items.NAME_TAG : egg);
		icon.set(DataComponentTypes.CUSTOM_NAME, target.entityType().getName().copy().formatted(Formatting.AQUA));
		return icon;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (slotIndex >= 0 && slotIndex < this.targets.size()
				&& !this.icons.getStack(slotIndex).isEmpty()
				&& player instanceof ServerPlayerEntity admin) {
			Target target = this.targets.get(slotIndex);
			ServerScheduler.schedule(1, () -> run(admin, target));
		}

		syncState();
	}

	private void run(ServerPlayerEntity admin, Target target) {
		if (admin.isRemoved()) {
			return;
		}

		admin.closeHandledScreen();

		if (target.item() != null) {
			int haul = MagnetSweep.sweepItem(admin, target.item(), this.range);
			Text name = new ItemStack(target.item()).getName();

			if (haul == 0) {
				admin.sendMessage(Text.translatable("message.slickfun.magnet.empty", name, this.range)
						.formatted(Formatting.GRAY), false);
				return;
			}

			admin.sendMessage(Text.translatable("message.slickfun.magnet.hauled", haul, name, this.range)
					.formatted(Formatting.LIGHT_PURPLE), false);
			boom(admin, 1.4F);
			return;
		}

		int pulled = target.playerName() != null
				? MagnetSweep.sweepEntities(admin, entity -> entity instanceof ServerPlayerEntity other
						&& other.getGameProfile().getName().equals(target.playerName()), this.range)
				: MagnetSweep.sweepEntities(admin, entity -> entity.getType() == target.entityType(), this.range);

		Text what = target.playerName() != null
				? Text.literal(target.playerName())
				: target.entityType().getName();

		if (pulled == 0) {
			admin.sendMessage(Text.translatable("message.slickfun.magnet.no_entities", what, this.range)
					.formatted(Formatting.GRAY), false);
			return;
		}

		admin.sendMessage(Text.translatable("message.slickfun.magnet.dragged", pulled, what)
				.formatted(Formatting.LIGHT_PURPLE), false);
		boom(admin, 0.8F);
	}

	private static void boom(ServerPlayerEntity admin, float pitch) {
		admin.getServerWorld().playSound(null, admin.getX(), admin.getY(), admin.getZ(),
				SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0F, pitch);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return false;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
