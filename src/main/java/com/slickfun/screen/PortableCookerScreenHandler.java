package com.slickfun.screen;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.slickfun.item.CookerType;
import com.slickfun.item.PortableCookerItem;
import com.slickfun.util.CookState;
import com.slickfun.util.CookingLogic;
import com.slickfun.util.ToolHost;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * A furnace with no furnace. Borrows the vanilla screen handler wholesale - slot layout,
 * output slot behaviour, shift-click routing - and supplies its own inventory and property
 * delegate, loaded from and saved back to the item stack.
 *
 * <p>Cooking only advances while the screen is open. Progressing it in the background would
 * mean rewriting the item's components every tick, which resyncs the whole stack to the
 * client every tick. Close it and the counters pause. The Kiln <em>block</em> has no such
 * limit - that is the trade for carrying one around.
 */
public class PortableCookerScreenHandler extends AbstractFurnaceScreenHandler {
	private static final int BURN_TIME = 0;
	private static final int FUEL_TIME = 1;
	private static final int COOK_TIME = 2;
	private static final int COOK_TIME_TOTAL = 3;

	/** Every open cooker, ticked once per server tick. */
	private static final Set<PortableCookerScreenHandler> OPEN = ConcurrentHashMap.newKeySet();

	private final CookerType type;
	private final Inventory contents;
	private final PropertyDelegate properties;
	private final ToolHost host;

	private float pendingExperience;

	public PortableCookerScreenHandler(CookerType type, int syncId, PlayerInventory playerInventory,
			Inventory contents, PropertyDelegate properties, ToolHost host) {
		super(type.screenType(), type.recipeType(), type.category(), syncId, playerInventory, contents, properties);
		this.type = type;
		this.contents = contents;
		this.properties = properties;
		this.host = host;
		OPEN.add(this);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PortableCookerScreenHandler::tickAll);
	}

	public static SimpleInventory newInventory() {
		return new SimpleInventory(3);
	}

	public static ArrayPropertyDelegate newProperties() {
		return new ArrayPropertyDelegate(4);
	}

	private static void tickAll(MinecraftServer server) {
		OPEN.removeIf(handler -> !handler.tickCooking());
	}

	/** @return false if this handler is finished with and should be forgotten. */
	private boolean tickCooking() {
		if (!(getPlayer() instanceof ServerPlayerEntity player) || player.isRemoved() || player.currentScreenHandler != this) {
			return false;
		}

		ServerWorld world = player.getServerWorld();
		boolean changed = false;
		int burnTime = this.properties.get(BURN_TIME);

		if (burnTime > 0) {
			this.properties.set(BURN_TIME, --burnTime);
			changed = true;
		}

		Optional<RecipeEntry<? extends AbstractCookingRecipe>> recipe =
				CookingLogic.findRecipe(world, this.contents.getStack(CookingLogic.INPUT_SLOT), this.type);
		boolean canCraft = recipe.isPresent() && CookingLogic.outputHasRoom(world, recipe.get(), this.contents);

		if (canCraft) {
			if (burnTime <= 0) {
				int lit = CookingLogic.consumeFuel(this.contents);

				if (lit > 0) {
					this.properties.set(BURN_TIME, lit);
					this.properties.set(FUEL_TIME, lit);
					burnTime = lit;
					changed = true;
				}
			}

			if (burnTime > 0) {
				int total = Math.max(1, (int) (recipe.get().value().getCookingTime() / this.type.speed()));
				this.properties.set(COOK_TIME_TOTAL, total);

				int cookTime = this.properties.get(COOK_TIME) + 1;

				if (cookTime >= total) {
					awardExperience(player, CookingLogic.craft(world, recipe.get(), this.contents));
					this.properties.set(COOK_TIME, 0);
				} else {
					this.properties.set(COOK_TIME, cookTime);
				}

				changed = true;
			} else if (this.properties.get(COOK_TIME) > 0) {
				this.properties.set(COOK_TIME, Math.max(0, this.properties.get(COOK_TIME) - 2));
				changed = true;
			}
		} else if (this.properties.get(COOK_TIME) != 0) {
			this.properties.set(COOK_TIME, 0);
			changed = true;
		}

		if (changed) {
			sendContentUpdates();
		}

		return true;
	}

	private void awardExperience(ServerPlayerEntity player, float earned) {
		this.pendingExperience += earned;

		if (this.pendingExperience >= 1.0F) {
			int whole = (int) this.pendingExperience;
			this.pendingExperience -= whole;
			player.addExperience(whole);
		}
	}

	private PlayerEntity getPlayer() {
		// AbstractFurnaceScreenHandler keeps no player reference, so recover it from a slot.
		return this.slots.size() > 3 && this.slots.get(3).inventory instanceof PlayerInventory inventory
				? inventory.player
				: null;
	}

	@Override
	protected boolean isSmeltable(ItemStack stack) {
		if (!(getPlayer() instanceof ServerPlayerEntity player)) {
			return super.isSmeltable(stack);
		}

		return CookingLogic.findRecipe(player.getServerWorld(), stack, this.type).isPresent();
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.host.isValid() && this.host.stack().getItem() instanceof PortableCookerItem;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		OPEN.remove(this);

		if (this.host.isValid() && this.host.stack().getItem() instanceof PortableCookerItem) {
			PortableCookerItem.save(this.host.stack(), this.contents, new CookState(
					this.properties.get(BURN_TIME),
					this.properties.get(FUEL_TIME),
					this.properties.get(COOK_TIME)));
			this.host.markChanged();
		} else {
			// The cooker went somewhere mid-session; don't swallow the contents.
			for (int slot = 0; slot < this.contents.size(); slot++) {
				ItemStack stray = this.contents.getStack(slot);

				if (!stray.isEmpty() && !player.getInventory().insertStack(stray)) {
					player.dropItem(stray, false);
				}
			}
		}

		this.contents.clear();
		super.onClosed(player);
	}
}
