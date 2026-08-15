package com.slickfun.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.slickfun.registry.ModComponents;
import com.slickfun.screen.QuiverScreenHandler;
import com.slickfun.util.CapturedMob;
import com.slickfun.util.ScaffoldManager;
import com.slickfun.util.ServerScheduler;

import net.minecraft.block.Blocks;
import net.minecraft.block.ConcretePowderBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** The last batch: quivers, belts, tethers, scaffolds and a very small army. */
public final class FinalTools {
	private FinalTools() {
	}

	private static void tip(List<Text> tooltip, String key) {
		tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
	}

	// ------------------------------------------------------------------ carried, passive

	/**
	 * Arrow storage that keeps your inventory stocked.
	 *
	 * <p>Rather than intercepting the bow - which would mean reaching into vanilla's ammo
	 * lookup and its consumption path separately, in two different classes - the quiver just
	 * pushes a stack out whenever your loose arrows run low. The bow keeps working exactly as
	 * it always has, and the arrows still show in your inventory where you can see them.
	 */
	public static class Quiver extends Item {
		public static final int SLOTS = 9;

		public Quiver(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);

			if (world.isClient) {
				return TypedActionResult.success(stack, true);
			}

			if (user instanceof ServerPlayerEntity player) {
				QuiverScreenHandler.open(player, hand);
			}

			return TypedActionResult.consume(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.quiver");
		}
	}

	/** Lets a Totem of Undying work from anywhere in your pack. Purely a marker. */
	public static class TotemBelt extends Item {
		public TotemBelt(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.totem_belt");
		}
	}

	/** Fired arrows come back to you instead of littering the ground. Purely a marker. */
	public static class ArrowRecoveryCharm extends Item {
		public ArrowRecoveryCharm(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.arrow_recovery");
		}
	}

	/** A chance at the head of anything you kill yourself. Purely a marker. */
	public static class HeadHuntersCharm extends Item {
		public HeadHuntersCharm(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.head_hunter");
		}
	}

	// ------------------------------------------------------------------ herding

	/**
	 * Picks up every animal you have on a lead at once and carries them as one item.
	 *
	 * <p>The mobs are stored the same way the Poke Ball stores its catch, so names, ages,
	 * health, tamed owners and breeding cooldowns all survive the trip.
	 */
	public static class LeashAnchor extends Item {
		private static final int MAX_HERD = 12;
		private static final double LEASH_RANGE = 12.0D;

		public LeashAnchor(Settings settings) {
			super(settings);
		}

		public static int herdSize(ItemStack stack) {
			return stack.getOrDefault(ModComponents.LEASHED_HERD, List.of()).size();
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

			return herdSize(stack) > 0 ? release(serverWorld, player, stack) : gather(serverWorld, player, stack);
		}

		private TypedActionResult<ItemStack> gather(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
			Box area = player.getBoundingBox().expand(LEASH_RANGE);
			List<Entity> leashed = world.getOtherEntities(player, area,
					entity -> entity instanceof Leashable leash && leash.getLeashHolder() == player);

			if (leashed.isEmpty()) {
				player.sendMessage(Text.translatable("message.slickfun.anchor.nothing").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			List<CapturedMob> herd = new ArrayList<>();

			for (Entity entity : leashed) {
				if (herd.size() >= MAX_HERD) {
					break;
				}

				// Unhook before saving, or the stored data remembers a lead that will not exist
				// when the mob comes back out. The lead itself drops so it is not lost.
				((Leashable) entity).detachLeash(true, true);

				Optional<CapturedMob> captured = CapturedMob.of(entity);

				if (captured.isPresent()) {
					herd.add(captured.get());
					entity.discard();
				}
			}

			if (herd.isEmpty()) {
				player.sendMessage(Text.translatable("message.slickfun.anchor.nothing").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			stack.set(ModComponents.LEASHED_HERD, List.copyOf(herd));
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.PLAYERS, 1.0F, 1.2F);
			player.sendMessage(Text.translatable("message.slickfun.anchor.gathered", herd.size())
					.formatted(Formatting.GREEN), true);

			return TypedActionResult.success(stack);
		}

		private TypedActionResult<ItemStack> release(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
			List<CapturedMob> herd = stack.getOrDefault(ModComponents.LEASHED_HERD, List.of());
			Vec3d spot = player.getPos().add(player.getRotationVec(1.0F).multiply(1.5D));
			int freed = 0;

			for (CapturedMob mob : herd) {
				if (mob.release(world, spot)) {
					freed++;
				}
			}

			stack.remove(ModComponents.LEASHED_HERD);
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.sendMessage(Text.translatable("message.slickfun.anchor.released", freed).formatted(Formatting.AQUA), true);

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			int held = herdSize(stack);

			if (held > 0) {
				tooltip.add(Text.translatable("tooltip.slickfun.anchor.holding", held).formatted(Formatting.AQUA));
			}

			tip(tooltip, "tooltip.slickfun.leash_anchor");
		}
	}

	// ------------------------------------------------------------------ travel

	/**
	 * Swaps places with another player.
	 *
	 * <p>Consent is the whole design problem here, and sneaking solves it without a prompt
	 * flow: the target has to deliberately hold crouch, which nobody does by accident, and
	 * they can refuse simply by standing up.
	 */
	public static class EnderTether extends Item {
		public EnderTether(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
			if (user.getWorld().isClient) {
				return ActionResult.SUCCESS;
			}

			if (!(user instanceof ServerPlayerEntity player) || !(entity instanceof ServerPlayerEntity target)) {
				return ActionResult.PASS;
			}

			if (target.getWorld() != player.getWorld()) {
				player.sendMessage(Text.translatable("message.slickfun.tether.far").formatted(Formatting.GRAY), true);
				return ActionResult.FAIL;
			}

			if (!target.isSneaking()) {
				player.sendMessage(Text.translatable("message.slickfun.tether.consent", target.getName())
						.formatted(Formatting.GRAY), true);
				target.sendMessage(Text.translatable("message.slickfun.tether.asked", player.getName())
						.formatted(Formatting.LIGHT_PURPLE), true);
				return ActionResult.FAIL;
			}

			Vec3d here = player.getPos();
			Vec3d there = target.getPos();
			ServerWorld world = player.getServerWorld();

			puff(world, here);
			puff(world, there);

			player.teleport(world, there.x, there.y, there.z, player.getYaw(), player.getPitch());
			target.teleport(world, here.x, here.y, here.z, target.getYaw(), target.getPitch());

			world.playSound(null, here.x, here.y, here.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
			world.playSound(null, there.x, there.y, there.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);

			stack.decrement(1);
			return ActionResult.SUCCESS;
		}

		private static void puff(ServerWorld world, Vec3d at) {
			world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0D, at.z, 40, 0.4D, 0.8D, 0.4D, 0.4D);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.ender_tether");
		}
	}

	/**
	 * The Infinite Ladders, turned on their side: a temporary floor that fades on a timer.
	 *
	 * <p>The platform is glass rather than actual scaffolding. Scaffolding with nothing under
	 * it turns into a falling block and drops itself, which would have made this a scaffolding
	 * printer as well as a bridge; glass just sits there until the timer takes it away.
	 */
	public static class ScaffoldWand extends Item {
		private static final int RADIUS = 1;

		public ScaffoldWand(Settings settings) {
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

			BlockPos centre = player.getBlockPos().down();
			List<BlockPos> placed = new ArrayList<>();

			for (int x = -RADIUS; x <= RADIUS; x++) {
				for (int z = -RADIUS; z <= RADIUS; z++) {
					BlockPos pos = centre.add(x, 0, z);

					// Never over anything: this fills gaps, it does not replace floors.
					if (serverWorld.getBlockState(pos).isReplaceable() && serverWorld.isInBuildLimit(pos)) {
						serverWorld.setBlockState(pos, ScaffoldManager.PLATFORM.getDefaultState());
						placed.add(pos);
					}
				}
			}

			// Claimed even when nothing new fitted, so standing mid-bridge and clicking pushes
			// the timer back rather than doing nothing while the span behind you runs out.
			ScaffoldManager.claim(player, placed);
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0F, 1.4F);

			if (placed.isEmpty()) {
				player.sendMessage(Text.translatable("message.slickfun.scaffold.refreshed").formatted(Formatting.GRAY), true);
			}

			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.scaffold_wand", ScaffoldManager.LIFETIME_TICKS / 20)
					.formatted(Formatting.GRAY));
		}
	}

	// ------------------------------------------------------------------ building aid

	/**
	 * Sets every bag of concrete powder you are carrying, all at once.
	 *
	 * <p>The hardened form is found by dropping {@code _powder} off the powder's own id rather
	 * than from a table of sixteen colours. All the vanilla dyes follow that rule, and so does
	 * any colour a datapack or another mod adds, which a hand-written table would miss.
	 */
	public static class ConcreteFlooder extends Item {
		private static final int COOLDOWN_TICKS = 20;
		private static final String POWDER_SUFFIX = "_powder";

		public ConcreteFlooder(Settings settings) {
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

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			int setCount = 0;

			for (int slot = 0; slot < player.getInventory().size(); slot++) {
				ItemStack bag = player.getInventory().getStack(slot);
				Item hardened = hardenedFor(bag);

				if (hardened == null) {
					continue;
				}

				setCount += bag.getCount();
				// A fresh stack rather than an edit: powder and concrete are different items.
				player.getInventory().setStack(slot, new ItemStack(hardened, bag.getCount()));
			}

			if (setCount == 0) {
				player.sendMessage(Text.translatable("message.slickfun.flooder.nothing").formatted(Formatting.GRAY), true);
				return TypedActionResult.fail(stack);
			}

			player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, 0.8F);
			serverWorld.spawnParticles(ParticleTypes.SPLASH,
					player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.6D, 0.6D, 0.6D, 0.1D);
			player.sendMessage(Text.translatable("message.slickfun.flooder.set", setCount).formatted(Formatting.AQUA), true);

			return TypedActionResult.success(stack);
		}

		/** The concrete this powder would harden into, or null if it is not concrete powder. */
		private static Item hardenedFor(ItemStack stack) {
			if (!(stack.getItem() instanceof BlockItem blockItem)
					|| !(blockItem.getBlock() instanceof ConcretePowderBlock)) {
				return null;
			}

			Identifier powder = Registries.ITEM.getId(stack.getItem());

			if (!powder.getPath().endsWith(POWDER_SUFFIX)) {
				return null;
			}

			String hardenedPath = powder.getPath().substring(0, powder.getPath().length() - POWDER_SUFFIX.length());
			Item hardened = Registries.ITEM.get(Identifier.of(powder.getNamespace(), hardenedPath));

			return hardened == Items.AIR ? null : hardened;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.concrete_flooder");
		}
	}

	/**
	 * A plumb line and a grid, drawn in particles.
	 *
	 * <p>Rendered server side rather than with a client mod, so it works for anyone on the
	 * server with nothing installed. That caps it at what particles can do: it draws once and
	 * lingers for a few seconds rather than tracking you continuously.
	 */
	public static class PlumbBob extends Item {
		private static final int DROP = 32;
		private static final int GRID = 8;
		private static final int REPEATS = 5;
		private static final int SPACING_TICKS = 10;

		public PlumbBob(Settings settings) {
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

			if (player.getItemCooldownManager().isCoolingDown(this)) {
				return TypedActionResult.fail(stack);
			}

			player.getItemCooldownManager().set(this, REPEATS * SPACING_TICKS);
			BlockPos anchor = player.getBlockPos();

			for (int repeat = 0; repeat < REPEATS; repeat++) {
				ServerScheduler.schedule(repeat * SPACING_TICKS, () -> draw(serverWorld, anchor));
			}

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7F, 1.6F);

			return TypedActionResult.success(stack);
		}

		private static void draw(ServerWorld world, BlockPos anchor) {
			DustParticleEffect line = new DustParticleEffect(new Vector3f(1.0F, 0.25F, 0.25F), 1.0F);
			DustParticleEffect grid = new DustParticleEffect(new Vector3f(0.3F, 0.7F, 1.0F), 1.0F);

			double x = anchor.getX() + 0.5D;
			double z = anchor.getZ() + 0.5D;

			// The plumb line itself, straight down until it meets something.
			for (int depth = 0; depth < DROP; depth++) {
				BlockPos below = anchor.down(depth);

				world.spawnParticles(line, x, below.getY() + 0.5D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

				if (depth > 0 && !world.getBlockState(below).isReplaceable()) {
					break;
				}
			}

			// A grid on the player's own level, for squaring walls up against.
			for (int offset = -GRID; offset <= GRID; offset++) {
				world.spawnParticles(grid, x + offset, anchor.getY() + 0.2D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				world.spawnParticles(grid, x, anchor.getY() + 0.2D, z + offset, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tip(tooltip, "tooltip.slickfun.plumb_bob");
		}
	}

	// ------------------------------------------------------------------ the army

	/**
	 * A squad of iron golems, on loan.
	 *
	 * <p>They are flagged player-created so they never turn on you, and they are pulled back
	 * out of the world on a timer - otherwise one stack of these would permanently carpet a
	 * base in golems and end mob spawning for good.
	 */
	public static class PortableArmy extends Item {
		private static final int SQUAD = 4;
		private static final int DUTY_TICKS = 20 * 60;
		private static final int RETARGET_TICKS = 40;
		private static final double SEEK_RANGE = 20.0D;

		public PortableArmy(Settings settings) {
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

			List<IronGolemEntity> squad = new ArrayList<>();

			for (int index = 0; index < SQUAD; index++) {
				double angle = index * (2 * Math.PI / SQUAD);
				Vec3d spot = player.getPos().add(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D);
				IronGolemEntity golem = EntityType.IRON_GOLEM.create(serverWorld);

				if (golem == null) {
					continue;
				}

				golem.refreshPositionAndAngles(spot.x, spot.y, spot.z, serverWorld.random.nextFloat() * 360.0F, 0.0F);
				// Without this they treat players as valid targets and will turn on their owner.
				golem.setPlayerCreated(true);
				golem.setPersistent();
				golem.setCustomName(Text.translatable("entity.slickfun.recruit", player.getName())
						.formatted(Formatting.GOLD));

				serverWorld.spawnEntity(golem);
				serverWorld.spawnParticles(ParticleTypes.CLOUD, spot.x, spot.y + 1.0D, spot.z, 20, 0.3D, 0.5D, 0.3D, 0.05D);
				squad.add(golem);
			}

			if (squad.isEmpty()) {
				return TypedActionResult.fail(stack);
			}

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.PLAYERS, 1.0F, 0.8F);
			player.sendMessage(Text.translatable("message.slickfun.army.rally", squad.size()).formatted(Formatting.GOLD), true);

			patrol(serverWorld, squad, 0);
			stack.decrement(1);

			return TypedActionResult.success(stack);
		}

		/** Keeps pointing them at something to hit, then dismisses them when the tour is up. */
		private static void patrol(ServerWorld world, List<IronGolemEntity> squad, int elapsed) {
			ServerScheduler.schedule(RETARGET_TICKS, () -> {
				if (elapsed >= DUTY_TICKS) {
					for (IronGolemEntity golem : squad) {
						if (golem.isAlive()) {
							world.spawnParticles(ParticleTypes.POOF, golem.getX(), golem.getY() + 1.0D, golem.getZ(),
									15, 0.3D, 0.5D, 0.3D, 0.02D);
							golem.discard();
						}
					}

					return;
				}

				for (IronGolemEntity golem : squad) {
					if (!golem.isAlive() || golem.getTarget() != null && golem.getTarget().isAlive()) {
						continue;
					}

					HostileEntity quarry = world.getClosestEntity(HostileEntity.class,
							net.minecraft.entity.ai.TargetPredicate.DEFAULT, golem,
							golem.getX(), golem.getY(), golem.getZ(),
							golem.getBoundingBox().expand(SEEK_RANGE));

					if (quarry != null) {
						golem.setTarget(quarry);
					}
				}

				patrol(world, squad, elapsed + RETARGET_TICKS);
			});
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("tooltip.slickfun.portable_army", SQUAD, DUTY_TICKS / 20)
					.formatted(Formatting.GRAY));
		}
	}
}
