package com.slickfun.registry;

import com.slickfun.SlickFunMod;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

public final class ModGameRules {
	/**
	 * When false, creeper explosions stop breaking blocks but still hurt whatever is nearby.
	 * Unlike {@code mobGriefing} this leaves snow golems, villager farming and enderman
	 * block-moving alone, so it does not break farms built on those.
	 */
	public static final GameRules.Key<GameRules.BooleanRule> CREEPER_BLOCK_DAMAGE =
			GameRuleRegistry.register("slickfunCreeperBlockDamage", GameRules.Category.MOBS,
					GameRuleFactory.createBooleanRule(true));

	private ModGameRules() {
	}

	public static void register() {
		SlickFunMod.LOGGER.info("Registered game rules.");
	}
}
