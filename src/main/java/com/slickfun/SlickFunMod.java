package com.slickfun;

import com.slickfun.command.SlickFunCommands;
import com.slickfun.registry.ModArmorMaterials;
import com.slickfun.registry.ModBlocks;
import com.slickfun.registry.ModComponents;
import com.slickfun.registry.ModEntities;
import com.slickfun.registry.ModGameRules;
import com.slickfun.registry.ModItemGroups;
import com.slickfun.registry.ModItems;
import com.slickfun.registry.ModRecipes;
import com.slickfun.registry.ModTrades;
import com.slickfun.registry.ModVillagers;
import com.slickfun.screen.PortableCookerScreenHandler;
import com.slickfun.update.UpdateChecker;
import com.slickfun.update.UpdateNotifier;
import com.slickfun.update.UpdateSwapper;
import com.slickfun.util.AutoStorageManager;
import com.slickfun.util.BlockOwnership;
import com.slickfun.util.BoatManager;
import com.slickfun.util.CatCharmManager;
import com.slickfun.util.GearManager;
import com.slickfun.util.ScaffoldManager;
import com.slickfun.util.CharmManager;
import com.slickfun.util.ChairManager;
import com.slickfun.util.GoonManager;
import com.slickfun.util.HotTubManager;
import com.slickfun.util.LadderManager;
import com.slickfun.util.MagnetDrag;
import com.slickfun.util.MagnetFlight;
import com.slickfun.util.MagnetManager;
import com.slickfun.util.PrankManager;
import com.slickfun.util.RpgManager;
import com.slickfun.util.ServerScheduler;
import com.slickfun.util.WeaponManager;
import com.slickfun.util.SoulTieManager;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlickFunMod implements ModInitializer {
	public static final String MOD_ID = "slickfun";
	public static final Logger LOGGER = LoggerFactory.getLogger("Slick Fun Mod");

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.register();
		ModGameRules.register();
		ModBlocks.register();
		ModArmorMaterials.register();
		ModEntities.register();
		ModItems.register();
		ModItemGroups.register();
		ModRecipes.register();
		ModVillagers.register();
		ModTrades.register();

		ServerScheduler.register();
		HotTubManager.register();
		MagnetManager.register();
		ChairManager.register();
		LadderManager.register();
		CatCharmManager.register();
		CharmManager.register();
		GearManager.register();
		ScaffoldManager.register();
		BlockOwnership.register();
		SoulTieManager.register();
		PrankManager.register();
		MagnetFlight.register();
		MagnetDrag.register();
		RpgManager.register();
		WeaponManager.register();
		com.slickfun.item.ExtinguisherItem.register();
		GoonManager.register();
		AutoStorageManager.register();
		BoatManager.register();
		PortableCookerScreenHandler.register();
		SlickFunCommands.register();
		UpdateNotifier.register();
		UpdateChecker.register();
		UpdateSwapper.register();

		LOGGER.info("Slick Fun Mod loaded. Stay hydrated, die responsibly.");
	}
}
