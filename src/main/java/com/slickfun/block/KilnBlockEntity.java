package com.slickfun.block;

import java.util.Optional;

import com.slickfun.item.CookerType;
import com.slickfun.registry.ModBlocks;
import com.slickfun.screen.KilnScreenHandler;
import com.slickfun.util.CookingLogic;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Kiln's guts. Deliberately not an {@code AbstractFurnaceBlockEntity} subclass: that
 * class hard-wires its recipe lookup to a single recipe type through private fields, and the
 * kiln needs to filter what it accepts. Implementing {@link SidedInventory} directly gets
 * hopper support - in the top, fuel from the sides, results out of the bottom - for free.
 */
public class KilnBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
	private static final int[] TOP_SLOTS = {CookingLogic.INPUT_SLOT};
	private static final int[] SIDE_SLOTS = {CookingLogic.FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {CookingLogic.OUTPUT_SLOT, CookingLogic.FUEL_SLOT};

	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);

	private int burnTime;
	private int fuelTime;
	private int cookTime;
	private int cookTimeTotal = 200;
	private float storedExperience;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> KilnBlockEntity.this.burnTime;
				case 1 -> KilnBlockEntity.this.fuelTime;
				case 2 -> KilnBlockEntity.this.cookTime;
				case 3 -> KilnBlockEntity.this.cookTimeTotal;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> KilnBlockEntity.this.burnTime = value;
				case 1 -> KilnBlockEntity.this.fuelTime = value;
				case 2 -> KilnBlockEntity.this.cookTime = value;
				case 3 -> KilnBlockEntity.this.cookTimeTotal = value;
				default -> {
				}
			}
		}

		@Override
		public int size() {
			return 4;
		}
	};

	public KilnBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.KILN_BLOCK_ENTITY, pos, state);
	}

	public boolean isBurning() {
		return this.burnTime > 0;
	}

	public static void tick(World world, BlockPos pos, BlockState state, KilnBlockEntity kiln) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		boolean wasBurning = kiln.isBurning();
		boolean dirty = false;

		if (kiln.burnTime > 0) {
			kiln.burnTime--;
		}

		Optional<RecipeEntry<? extends AbstractCookingRecipe>> recipe =
				CookingLogic.findRecipe(serverWorld, kiln.inventory.get(CookingLogic.INPUT_SLOT), CookerType.KILN);
		boolean canCraft = recipe.isPresent() && CookingLogic.outputHasRoom(serverWorld, recipe.get(), kiln);

		if (canCraft) {
			if (kiln.burnTime <= 0) {
				int lit = CookingLogic.consumeFuel(kiln);

				if (lit > 0) {
					kiln.burnTime = lit;
					kiln.fuelTime = lit;
					dirty = true;
				}
			}

			if (kiln.burnTime > 0) {
				kiln.cookTimeTotal = Math.max(1, (int) (recipe.get().value().getCookingTime() / CookerType.KILN.speed()));
				kiln.cookTime++;

				if (kiln.cookTime >= kiln.cookTimeTotal) {
					kiln.storedExperience += CookingLogic.craft(serverWorld, recipe.get(), kiln);
					kiln.cookTime = 0;
				}

				dirty = true;
			} else if (kiln.cookTime > 0) {
				kiln.cookTime = Math.max(0, kiln.cookTime - 2);
			}
		} else if (kiln.cookTime != 0) {
			kiln.cookTime = 0;
			dirty = true;
		}

		if (wasBurning != kiln.isBurning()) {
			dirty = true;
			world.setBlockState(pos, state.with(KilnBlock.LIT, kiln.isBurning()), net.minecraft.block.Block.NOTIFY_ALL);
		}

		if (dirty) {
			markDirty(world, pos, state);
		}
	}

	/** Hands over whatever smelting experience has accumulated, like a real furnace does. */
	public void dropExperience(ServerWorld world, Vec3d pos) {
		int whole = (int) this.storedExperience;

		if (whole > 0) {
			ExperienceOrbEntity.spawn(world, pos, whole);
		}

		this.storedExperience = 0.0F;
	}

	// ------------------------------------------------------------------ inventory

	public DefaultedList<ItemStack> contents() {
		return this.inventory;
	}

	@Override
	public int size() {
		return this.inventory.size();
	}

	@Override
	public boolean isEmpty() {
		return this.inventory.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.inventory.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		return Inventories.splitStack(this.inventory, slot, amount);
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(this.inventory, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		ItemStack existing = this.inventory.get(slot);
		boolean sameItem = !stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, stack);
		this.inventory.set(slot, stack);
		stack.capCount(this.getMaxCountPerStack());

		if (slot == CookingLogic.INPUT_SLOT && !sameItem) {
			this.cookTime = 0;
			this.markDirty();
		}
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.world != null
				&& this.world.getBlockEntity(this.pos) == this
				&& player.squaredDistanceTo(Vec3d.ofCenter(this.pos)) <= 64.0D;
	}

	@Override
	public void clear() {
		this.inventory.clear();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return switch (slot) {
			case CookingLogic.OUTPUT_SLOT -> false;
			case CookingLogic.FUEL_SLOT -> CookingLogic.fuelTimeOf(stack) > 0;
			default -> true;
		};
	}

	// ------------------------------------------------------------------ hoppers

	@Override
	public int[] getAvailableSlots(Direction side) {
		return switch (side) {
			case DOWN -> BOTTOM_SLOTS;
			case UP -> TOP_SLOTS;
			default -> SIDE_SLOTS;
		};
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction direction) {
		return isValid(slot, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction direction) {
		// Only let a hopper pull an empty bucket back out of the fuel slot.
		if (direction == Direction.DOWN && slot == CookingLogic.FUEL_SLOT) {
			return stack.isOf(net.minecraft.item.Items.BUCKET);
		}

		return true;
	}

	// ------------------------------------------------------------------ persistence

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.inventory.clear();
		Inventories.readNbt(nbt, this.inventory, registries);
		this.burnTime = nbt.getInt("BurnTime");
		this.fuelTime = nbt.getInt("FuelTime");
		this.cookTime = nbt.getInt("CookTime");
		this.cookTimeTotal = Math.max(1, nbt.getInt("CookTimeTotal"));
		this.storedExperience = nbt.getFloat("Experience");
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, this.inventory, registries);
		nbt.putInt("BurnTime", this.burnTime);
		nbt.putInt("FuelTime", this.fuelTime);
		nbt.putInt("CookTime", this.cookTime);
		nbt.putInt("CookTimeTotal", this.cookTimeTotal);
		nbt.putFloat("Experience", this.storedExperience);
	}

	// ------------------------------------------------------------------ screen

	@Override
	public Text getDisplayName() {
		return Text.translatable("container.slickfun.kiln");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new KilnScreenHandler(syncId, playerInventory, this, this.properties);
	}
}
