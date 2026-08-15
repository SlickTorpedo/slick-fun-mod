package com.slickfun.registry;

import com.slickfun.SlickFunMod;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

/**
 * Custom damage types. The actual definitions live in
 * {@code data/slickfun/damage_type/*.json} so they get proper death messages, and they are
 * added to the vanilla bypass tags so armour, resistance and creative mode do not save you.
 */
public final class ModDamageTypes {
	public static final RegistryKey<DamageType> HOT_TUB = key("hot_tub");
	public static final RegistryKey<DamageType> CYANIDE = key("cyanide");
	public static final RegistryKey<DamageType> SOUL_SEVERED = key("soul_severed");
	public static final RegistryKey<DamageType> RAILGUN = key("railgun");

	private ModDamageTypes() {
	}

	private static RegistryKey<DamageType> key(String path) {
		return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, SlickFunMod.id(path));
	}

	public static DamageSource source(World world, RegistryKey<DamageType> key) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key));
	}
}
