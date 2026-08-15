package com.slickfun.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.slickfun.util.ServerScheduler;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** The rest of batch two: storage, movement and a panic button. */
public final class UtilityTools {
	private UtilityTools() {
	}

	/** Tidies a container: merges partial stacks, then sorts by name. */
	public static class SortingWand extends Item {
		public SortingWand(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();

			if (world.isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
				return ActionResult.FAIL;
			}

			if (!(world.getBlockEntity(context.getBlockPos()) instanceof Inventory container)) {
				player.sendMessage(Text.translatable("message.slickfun.sort.not_a_container").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			List<ItemStack> contents = new ArrayList<>();

			for (int slot = 0; slot < container.size(); slot++) {
				ItemStack stack = container.getStack(slot);

				if (!stack.isEmpty()) {
					contents.add(stack.copy());
				}
			}

			if (contents.isEmpty()) {
				return ActionResult.FAIL;
			}

			List<ItemStack> merged = merge(contents);
			merged.sort(Comparator.comparing(stack -> Registries.ITEM.getId(stack.getItem()).toString()));

			if (merged.size() > container.size()) {
				player.sendMessage(Text.translatable("message.slickfun.sort.too_full").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			container.clear();

			for (int slot = 0; slot < merged.size(); slot++) {
				container.setStack(slot, merged.get(slot));
			}

			container.markDirty();
			world.playSound(null, context.getBlockPos(), SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.8F, 1.4F);
			player.sendMessage(Text.translatable("message.slickfun.sort.done", merged.size()).formatted(Formatting.GRAY), true);

			return ActionResult.SUCCESS;
		}

		/** Combines stacks of the same item, respecting max stack size. */
		private static List<ItemStack> merge(List<ItemStack> contents) {
			List<ItemStack> merged = new ArrayList<>();

			for (ItemStack stack : contents) {
				boolean placed = false;

				for (ItemStack existing : merged) {
					if (ItemStack.areItemsAndComponentsEqual(existing, stack)
							&& existing.getCount() < existing.getMaxCount()) {
						int room = existing.getMaxCount() - existing.getCount();
						int move = Math.min(room, stack.getCount());
						existing.increment(move);
						stack.decrement(move);

						if (stack.isEmpty()) {
							placed = true;
							break;
						}
					}
				}

				if (!placed && !stack.isEmpty()) {
					merged.add(stack);
				}
			}

			return merged;
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.sorting_wand").formatted(Formatting.GRAY));
		}
	}

	/** Pulls you to whatever you are looking at, and forgives the landing. */
	public static class GrapplingHook extends Item {
		private static final double RANGE = 24.0D;
		private static final int COOLDOWN_TICKS = 40;
		private static final int GRACE_TICKS = 100;

		public GrapplingHook(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player)) {
				return TypedActionResult.fail(stack);
			}

			HitResult hit = player.raycast(RANGE, 1.0F, false);

			if (!(hit instanceof BlockHitResult block) || hit.getType() == HitResult.Type.MISS) {
				player.sendMessage(Text.translatable("message.slickfun.grapple.miss").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			Vec3d anchor = Vec3d.ofCenter(block.getBlockPos());
			Vec3d pull = anchor.subtract(player.getPos());
			double distance = pull.length();

			if (distance < 1.5D) {
				return TypedActionResult.fail(stack);
			}

			// Arc upward a little so short hops clear the lip of what you grabbed.
			Vec3d launch = pull.normalize().multiply(Math.min(1.6D, 0.5D + distance * 0.08D));
			player.setVelocity(launch.x, Math.max(launch.y, 0.42D), launch.z);
			player.velocityModified = true;
			player.fallDistance = 0.0F;

			// Landing is part of the tool, so it should not be what kills you.
			forgiveFall(player, GRACE_TICKS);

			ServerWorld serverWorld = player.getServerWorld();
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1.0F, 1.4F);
			serverWorld.spawnParticles(ParticleTypes.CRIT, anchor.x, anchor.y, anchor.z, 12, 0.2D, 0.2D, 0.2D, 0.1D);

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			stack.damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
			return TypedActionResult.success(stack, false);
		}

		private static void forgiveFall(ServerPlayerEntity player, int ticks) {
			if (ticks <= 0 || player.isRemoved()) {
				return;
			}

			ServerScheduler.schedule(1, () -> {
				if (player.isRemoved()) {
					return;
				}

				player.fallDistance = 0.0F;

				if (!player.isOnGround()) {
					forgiveFall(player, ticks - 1);
				}
			});
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.grappling_hook.1", (int) RANGE).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.grappling_hook.2").formatted(Formatting.DARK_GRAY));
		}
	}

	/** Everything hostile nearby goes flying, and you get a moment to breathe. */
	public static class PanicButton extends Item {
		private static final double RADIUS = 6.0D;
		private static final int COOLDOWN_TICKS = 20 * 60;

		public PanicButton(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
				return TypedActionResult.fail(stack);
			}

			Box area = player.getBoundingBox().expand(RADIUS);
			List<HostileEntity> nearby = serverWorld.getEntitiesByClass(HostileEntity.class, area, LivingEntity::isAlive);

			for (HostileEntity mob : nearby) {
				Vec3d away = mob.getPos().subtract(player.getPos());

				if (away.horizontalLengthSquared() < 1.0E-4D) {
					away = new Vec3d(serverWorld.getRandom().nextDouble() - 0.5D, 0.0D, serverWorld.getRandom().nextDouble() - 0.5D);
				}

				Vec3d push = away.multiply(1.0D, 0.0D, 1.0D).normalize().multiply(1.8D);
				mob.setVelocity(push.x, 0.7D, push.z);
				mob.velocityModified = true;
				mob.setAttacking(false);
			}

			player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 2, false, true, true));

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8F, 1.6F);
			serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
					player.getX(), player.getBodyY(0.5D), player.getZ(), 8, 1.0D, 0.5D, 1.0D, 0.0D);

			player.sendMessage(Text.translatable("message.slickfun.panic", nearby.size()).formatted(Formatting.GOLD), true);
			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

			return TypedActionResult.success(stack, false);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.panic_button.1", (int) RADIUS).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.slickfun.panic_button.2").formatted(Formatting.DARK_GRAY));
		}
	}
}
