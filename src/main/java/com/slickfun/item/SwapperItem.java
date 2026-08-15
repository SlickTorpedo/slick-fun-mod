package com.slickfun.item;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.TargetHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Trades places with whatever you are looking at. Works across the room or across the map. */
public class SwapperItem extends AdminItem {
	private static final double RANGE = 96.0D;
	private static final int COOLDOWN_TICKS = 60;

	public SwapperItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "swapper";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!AdminUtil.checkAdmin(user)) {
			return TypedActionResult.fail(stack);
		}

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		Entity target = TargetHelper.findTarget(user, RANGE);

		if (target == null) {
			user.sendMessage(Text.translatable("message.slickfun.no_target").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		if (target.getWorld() != world) {
			return TypedActionResult.fail(stack);
		}

		ServerWorld serverWorld = (ServerWorld) world;
		Vec3d userPos = user.getPos();
		Vec3d targetPos = target.getPos();

		puff(serverWorld, userPos);
		puff(serverWorld, targetPos);

		target.requestTeleport(userPos.x, userPos.y, userPos.z);
		user.requestTeleport(targetPos.x, targetPos.y, targetPos.z);

		target.fallDistance = 0.0F;
		user.fallDistance = 0.0F;

		serverWorld.playSound(null, userPos.x, userPos.y, userPos.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		serverWorld.playSound(null, targetPos.x, targetPos.y, targetPos.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);

		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		user.swingHand(hand, true);
		return TypedActionResult.success(stack, false);
	}

	private static void puff(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0D, pos.z, 60, 0.4D, 0.8D, 0.4D, 0.4D);
	}
}
