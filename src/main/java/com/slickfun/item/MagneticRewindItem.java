package com.slickfun.item;

import com.slickfun.util.AdminUtil;
import com.slickfun.util.MagnetSweep;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Undoes the last Admin Vacuum sweep, putting every stack back where it was taken from.
 *
 * <p>It can only give back what it can still find - items are reclaimed from the admin first,
 * then off the floor nearby. Anything already spent, or handed to someone else, is gone, and
 * the count in the message says how much actually made it home.
 */
public class MagneticRewindItem extends AdminItem {
	public MagneticRewindItem(Settings settings) {
		super(settings);
	}

	@Override
	protected String tooltipKey() {
		return "magnetic_rewind";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}

		if (!AdminUtil.checkAdmin(user) || !(user instanceof ServerPlayerEntity admin)) {
			return TypedActionResult.fail(stack);
		}

		int returned = MagnetSweep.rewind(admin);

		if (returned < 0) {
			admin.sendMessage(Text.translatable("message.slickfun.rewind.nothing").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(stack);
		}

		admin.getServerWorld().playSound(null, admin.getX(), admin.getY(), admin.getZ(),
				SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 1.6F);
		admin.getServerWorld().spawnParticles(ParticleTypes.REVERSE_PORTAL,
				admin.getX(), admin.getY() + 1.0D, admin.getZ(), 60, 0.6D, 0.8D, 0.6D, 0.2D);
		admin.sendMessage(Text.translatable("message.slickfun.rewind.done", returned).formatted(Formatting.LIGHT_PURPLE), false);

		return TypedActionResult.success(stack);
	}
}
