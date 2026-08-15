package com.slickfun.entity;

import java.util.Optional;

import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModEntities;
import com.slickfun.registry.ModItems;
import com.slickfun.util.CapturedMob;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The thrown ball. An empty one catches the first thing it hits; a full one lets its
 * passenger out where it lands. Either way the ball itself ends up on the ground as an item,
 * so it is never destroyed by being thrown - only by being used to release something.
 */
public class PokeBallEntity extends ThrownItemEntity {
	public PokeBallEntity(EntityType<? extends PokeBallEntity> type, World world) {
		super(type, world);
	}

	public PokeBallEntity(World world, LivingEntity owner, ItemStack ball) {
		super(ModEntities.POKE_BALL, owner, world);
		setItem(ball.copyWithCount(1));
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.POKE_BALL;
	}

	private Optional<CapturedMob> passenger() {
		return Optional.ofNullable(getStack().get(ModComponents.CAPTURED_MOB));
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);

		if (getWorld().isClient) {
			return;
		}

		// A loaded ball is a delivery, not a capture - let the block-hit path handle it.
		if (passenger().isPresent()) {
			return;
		}

		Entity target = hit.getEntity();

		if (!CapturedMob.isCapturable(target)) {
			reject(target.getPos(), "message.slickfun.pokeball.refused");
			return;
		}

		Optional<CapturedMob> captured = CapturedMob.of(target);

		if (captured.isEmpty()) {
			reject(target.getPos(), "message.slickfun.pokeball.refused");
			return;
		}

		ItemStack loaded = new ItemStack(ModItems.POKE_BALL);
		loaded.set(ModComponents.CAPTURED_MOB, captured.get());

		ServerWorld world = (ServerWorld) getWorld();
		Vec3d where = target.getPos();

		world.spawnParticles(ParticleTypes.FLASH, where.x, where.y + 0.5D, where.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		world.spawnParticles(ParticleTypes.END_ROD, where.x, where.y + 0.5D, where.z, 40, 0.3D, 0.4D, 0.3D, 0.1D);
		world.playSound(null, where.x, where.y, where.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.6F);
		world.playSound(null, where.x, where.y, where.z, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.6F, 1.8F);

		target.discard();
		drop(loaded, where);
		announce(captured.get().name(), "message.slickfun.pokeball.caught");
		discard();
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);

		if (getWorld().isClient || !isAlive()) {
			return;
		}

		ServerWorld world = (ServerWorld) getWorld();
		Vec3d where = hit.getPos();
		Optional<CapturedMob> holding = passenger();

		if (holding.isPresent()) {
			if (holding.get().release(world, where)) {
				world.spawnParticles(ParticleTypes.CLOUD, where.x, where.y + 0.4D, where.z, 30, 0.3D, 0.3D, 0.3D, 0.05D);
				world.playSound(null, where.x, where.y, where.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 1.4F);
				announce(holding.get().name(), "message.slickfun.pokeball.released");
				// Used up: this is what makes the ball a consumable.
				discard();
				return;
			}

			// Could not rebuild the mob - give the ball back rather than eat it.
			drop(getStack().copy(), where);
			discard();
			return;
		}

		// Empty ball that hit the ground: it just lands.
		drop(new ItemStack(ModItems.POKE_BALL), where);
		world.playSound(null, where.x, where.y, where.z, SoundEvents.BLOCK_METAL_HIT, SoundCategory.PLAYERS, 0.6F, 1.4F);
		discard();
	}

	private void reject(Vec3d where, String key) {
		drop(new ItemStack(ModItems.POKE_BALL), where);

		if (getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
			player.sendMessage(Text.translatable(key).formatted(Formatting.GRAY), true);
		}

		getWorld().playSound(null, where.x, where.y, where.z, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.7F, 1.2F);
		discard();
	}

	private void drop(ItemStack stack, Vec3d where) {
		ItemEntity dropped = new ItemEntity(getWorld(), where.x, where.y + 0.25D, where.z, stack);
		dropped.setVelocity(0.0D, 0.1D, 0.0D);
		dropped.setPickupDelay(10);
		getWorld().spawnEntity(dropped);
	}

	private void announce(Text who, String key) {
		if (getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
			player.sendMessage(Text.translatable(key, who).formatted(Formatting.AQUA), true);
		}
	}
}
