package com.slickfun.util;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * A whole mob folded up into an item component.
 *
 * <p>Storing the entity's own save data means everything survives the trip: health, age,
 * name, tamed owner, villager profession and trades, horse stats, the lot.
 */
public record CapturedMob(Identifier type, NbtCompound data, Text name) {
	public static final Codec<CapturedMob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("type").forGetter(CapturedMob::type),
			NbtCompound.CODEC.fieldOf("data").forGetter(CapturedMob::data),
			net.minecraft.text.TextCodecs.CODEC.fieldOf("name").forGetter(CapturedMob::name)
	).apply(instance, CapturedMob::new));

	public static final PacketCodec<RegistryByteBuf, CapturedMob> PACKET_CODEC = PacketCodec.tuple(
			Identifier.PACKET_CODEC, CapturedMob::type,
			PacketCodecs.NBT_COMPOUND, CapturedMob::data,
			net.minecraft.text.TextCodecs.REGISTRY_PACKET_CODEC, CapturedMob::name,
			CapturedMob::new);

	/** Folds an entity away, or empty if it is something that must not be captured. */
	public static Optional<CapturedMob> of(Entity entity) {
		if (!isCapturable(entity)) {
			return Optional.empty();
		}

		NbtCompound nbt = new NbtCompound();

		if (!entity.saveSelfNbt(nbt)) {
			return Optional.empty();
		}

		// UUID must go, or releasing it would collide with the entity we are about to remove.
		nbt.remove("UUID");

		return Optional.of(new CapturedMob(
				Registries.ENTITY_TYPE.getId(entity.getType()), nbt, entity.getName().copy()));
	}

	public static boolean isCapturable(Entity entity) {
		return entity.isAlive()
				&& !entity.isPlayer()
				&& !entity.isSpectator()
				&& entity.getType().isSaveable()
				&& !entity.getType().equals(EntityType.ENDER_DRAGON)
				&& !entity.getType().equals(EntityType.WITHER);
	}

	/** Unfolds the mob back into the world. Returns false if the data no longer makes sense. */
	public boolean release(ServerWorld world, Vec3d pos) {
		Optional<EntityType<?>> entityType = EntityType.get(this.type.toString());

		if (entityType.isEmpty()) {
			return false;
		}

		NbtCompound nbt = this.data.copy();
		nbt.putString("id", this.type.toString());

		Entity entity = EntityType.loadEntityWithPassengers(nbt, world, spawned -> {
			spawned.refreshPositionAndAngles(pos.x, pos.y, pos.z, spawned.getYaw(), spawned.getPitch());
			return spawned;
		});

		if (entity == null) {
			return false;
		}

		world.spawnEntity(entity);
		return true;
	}
}
