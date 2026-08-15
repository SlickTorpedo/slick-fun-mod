package com.slickfun.item;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.TargetHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Calls down a lightning bolt on whatever - or whoever - you are pointing at. */
public class ThunderStaffItem extends AdminItem {
	private static final double RANGE = 64.0D;
	private static final int COOLDOWN_TICKS = 40;

	public ThunderStaffItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "thunder_staff";
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

		Vec3d strikePos = resolveStrikePos(world, user);

		if (strikePos == null) {
			return TypedActionResult.fail(stack);
		}

		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);

		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(strikePos);

			if (user instanceof ServerPlayerEntity serverPlayer) {
				bolt.setChanneler(serverPlayer);
			}

			world.spawnEntity(bolt);
		}

		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		user.swingHand(hand, true);
		return TypedActionResult.success(stack, false);
	}

	private static Vec3d resolveStrikePos(World world, PlayerEntity user) {
		Entity target = TargetHelper.findTarget(user, RANGE);

		if (target != null) {
			return target.getPos();
		}

		HitResult hit = user.raycast(RANGE, 1.0F, false);

		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}

		return hit.getPos();
	}
}
