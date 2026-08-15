package com.slickfun.registry;

import java.util.ArrayList;
import java.util.List;

import com.slickfun.SlickFunMod;
import com.slickfun.item.AirhornItem;
import com.slickfun.item.BoomboxItem;
import com.slickfun.item.BuildingTools;
import com.slickfun.item.FarmTools;
import com.slickfun.item.UtilityTools;
import com.slickfun.item.BulkStorageItems;
import com.slickfun.item.CatCollarItem;
import com.slickfun.item.CharmTools;
import com.slickfun.item.TravelTools;
import com.slickfun.item.ChairItem;
import com.slickfun.item.CookerType;
import com.slickfun.item.CostcoFoodItem;
import com.slickfun.item.CyanidePillItem;
import com.slickfun.item.DeathCompassItem;
import com.slickfun.item.FinalTools;
import com.slickfun.item.ExtinguisherItem;
import com.slickfun.item.GagItems;
import com.slickfun.item.Gadgets;
import com.slickfun.item.GunItems;
import com.slickfun.item.MoreGagItems;
import com.slickfun.item.MoreGuns;
import com.slickfun.item.RealSelfDestructButtonItem;
import com.slickfun.item.WeaponItems;
import com.slickfun.item.MoreJokeItems;
import com.slickfun.item.MoreTools;
import com.slickfun.item.InfiniteFireworkItem;
import com.slickfun.item.InfiniteLaddersItem;
import com.slickfun.item.ItemFinderItem;
import com.slickfun.item.ItemMagnetItem;
import com.slickfun.item.JokeItems;
import com.slickfun.item.PocketBedrollItem;
import com.slickfun.item.AdminMagnetItem;
import com.slickfun.item.MagneticRewindItem;
import com.slickfun.item.PrankItems;
import com.slickfun.item.SandBlasterItem;
import com.slickfun.item.ScubaTankItem;
import com.slickfun.item.SoulTieItem;
import com.slickfun.item.PokeBallItem;
import com.slickfun.item.PortableCookerItem;
import com.slickfun.item.WeatherStickItem;
import com.slickfun.item.PortableEnchantingTableItem;
import com.slickfun.item.PortableHotTubItem;
import com.slickfun.item.PortableToiletItem;
import com.slickfun.item.RepulsorBeamItem;
import com.slickfun.item.SimplePortableItem;
import com.slickfun.item.SwapperItem;
import com.slickfun.item.SwissArmyKnifeItem;
import com.slickfun.item.ThunderStaffItem;
import com.slickfun.item.TractorBeamItem;
import com.slickfun.item.YeetStickItem;
import com.slickfun.screen.PortableScreens;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	/** Everything the mod adds, in registration order. Fills the creative tab and admin menu. */
	public static final List<Item> ALL = new ArrayList<>();

	/**
	 * The portable utilities, in the order they occupy slots in the Swiss Army Knife. There
	 * are exactly nine, matching the nine slots of the knife's screen.
	 */
	public static final List<Item> TOOLKIT_ORDER = new ArrayList<>();

	// ---------------------------------------------------------------- respawn tools

	public static final Item PORTABLE_HOT_TUB = register("portable_hot_tub",
			new PortableHotTubItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item CYANIDE_PILL = register("cyanide_pill",
			new CyanidePillItem(new Item.Settings()
					.maxCount(16)
					.food(new FoodComponent.Builder()
							.nutrition(0)
							.saturationModifier(0.0F)
							.alwaysEdible()
							.snack()
							.build())));

	public static final Item POCKET_BEDROLL = register("pocket_bedroll",
			new PocketBedrollItem(new Item.Settings().maxCount(8)));

	// ---------------------------------------------------------------- pocket workstations
	// Registration order here IS the slot order inside the Swiss Army Knife.

	public static final Item PORTABLE_CRAFTING_TABLE = registerPortable("portable_crafting_table",
			new SimplePortableItem(portableSettings(), "portable_crafting_table", PortableScreens::openCrafting));

	public static final Item PORTABLE_ANVIL = registerPortable("portable_anvil",
			new SimplePortableItem(portableSettings(), "portable_anvil", PortableScreens::openAnvil));

	public static final Item PORTABLE_SMITHING_TABLE = registerPortable("portable_smithing_table",
			new SimplePortableItem(portableSettings(), "portable_smithing_table", PortableScreens::openSmithing));

	public static final Item PORTABLE_ENCHANTING_TABLE = registerPortable("portable_enchanting_table",
			new PortableEnchantingTableItem(portableSettings()));

	public static final Item PORTABLE_GRINDSTONE = registerPortable("portable_grindstone",
			new SimplePortableItem(portableSettings(), "portable_grindstone", PortableScreens::openGrindstone));

	public static final Item PORTABLE_STONECUTTER = registerPortable("portable_stonecutter",
			new SimplePortableItem(portableSettings(), "portable_stonecutter", PortableScreens::openStonecutter));

	public static final Item PORTABLE_ENDER_CHEST = registerPortable("portable_ender_chest",
			new SimplePortableItem(portableSettings(), "portable_ender_chest", PortableScreens::openEnderChest));

	public static final Item PORTABLE_TRASH_CAN = registerPortable("portable_trash_can",
			new SimplePortableItem(portableSettings(), "portable_trash_can", PortableScreens::openTrashCan));

	public static final Item PORTABLE_TOILET = registerPortable("portable_toilet",
			new PortableToiletItem(portableSettings()));

	// ---------------------------------------------------------------- pocket ovens
	// Knife-storable like the rest; their smelt state travels inside the knife with them.

	public static final Item PORTABLE_FURNACE = registerPortable("portable_furnace",
			new PortableCookerItem(portableSettings(), CookerType.FURNACE));

	public static final Item PORTABLE_SMOKER = registerPortable("portable_smoker",
			new PortableCookerItem(portableSettings(), CookerType.SMOKER));

	public static final Item PORTABLE_KILN = registerPortable("portable_kiln",
			new PortableCookerItem(portableSettings(), CookerType.KILN));

	// ---------------------------------------------------------------- placeable blocks

	public static final Item KILN = register("kiln", ModBlocks.createKilnItem());

	public static final Item DIAMOND_POOP = register("diamond_poop", ModBlocks.createDiamondPoopItem());

	public static final Item CAT_COLLAR = register("cat_collar",
			new CatCollarItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item POKE_BALL = register("poke_ball",
			new PokeBallItem(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));

	// ---------------------------------------------------------------- climbing

	public static final Item INFINITE_LADDERS = register("infinite_ladders",
			new InfiniteLaddersItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	// ---------------------------------------------------------------- items that look useful

	public static final Item HEAVY_STONE = register("heavy_stone",
			new JokeItems.HeavyStone(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item ITEM_DUPER = register("item_duper",
			new JokeItems.ItemDuper(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item TV_REMOTE = register("tv_remote",
			new JokeItems.TvRemote(new Item.Settings().maxCount(1)));

	public static final Item DARK_SHADES = register("dark_shades",
			new JokeItems.DarkShades(new Item.Settings().maxCount(1)));

	// ---------------------------------------------------------------- Costco food court

	public static final Item COSTCO_COOKIE = register("costco_cookie",
			new CostcoFoodItem(new Item.Settings().maxCount(16).food(CostcoFoodItem.foodComponent(4, 0.3F)),
					CostcoFoodItem.Flavour.COOKIE));

	public static final Item COSTCO_PIZZA_SLICE = register("costco_pizza_slice",
			new CostcoFoodItem(new Item.Settings().maxCount(16).food(CostcoFoodItem.foodComponent(8, 0.8F)),
					CostcoFoodItem.Flavour.PIZZA));

	public static final Item COSTCO_HOT_DOG = register("costco_hot_dog",
			new CostcoFoodItem(new Item.Settings().maxCount(16).food(CostcoFoodItem.foodComponent(6, 0.6F)),
					CostcoFoodItem.Flavour.HOT_DOG));

	// ---------------------------------------------------------------- carriers and gadgets

	public static final Item SWISS_ARMY_KNIFE = register("swiss_army_knife",
			new SwissArmyKnifeItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item ITEM_MAGNET = register("item_magnet",
			new ItemMagnetItem(new Item.Settings().maxCount(1)));

	public static final Item DEATH_COMPASS = register("death_compass",
			new DeathCompassItem(new Item.Settings().maxCount(1)));

	public static final Item WEATHER_STICK = register("weather_stick",
			new WeatherStickItem(new Item.Settings().maxCount(16)));

	public static final Item CHAIR = register("chair",
			new ChairItem(new Item.Settings().maxCount(1)));

	public static final Item BOOMBOX = register("boombox",
			new BoomboxItem(new Item.Settings().maxCount(1)));

	// ---------------------------------------------------------------- building and farming

	public static final Item BUILDER_WAND = register("builder_wand",
			new BuildingTools.BuilderWand(new Item.Settings().maxCount(1).maxDamage(512)));

	public static final Item EXCHANGE_WAND = register("exchange_wand",
			new BuildingTools.ExchangeWand(new Item.Settings().maxCount(1).maxDamage(512)));

	public static final Item PAINT_ROLLER = register("paint_roller",
			new BuildingTools.PaintRoller(new Item.Settings().maxCount(1)));

	public static final Item SAND_BLASTER = register("sand_blaster",
			new SandBlasterItem(new Item.Settings().maxCount(1).maxDamage(512)));

	public static final Item HARVEST_SICKLE = register("harvest_sickle",
			new FarmTools.HarvestSickle(new Item.Settings().maxCount(1).maxDamage(512)));

	public static final Item BONEMEAL_SPRAYER = register("bonemeal_sprayer",
			new FarmTools.BonemealSprayer(new Item.Settings().maxCount(1)));

	public static final Item COMPOSTER_WAND = register("composter_wand",
			new FarmTools.ComposterWand(new Item.Settings().maxCount(1)));

	public static final Item BEEHIVE_TOOL = register("beehive_tool",
			new FarmTools.BeehiveTool(new Item.Settings().maxCount(1).maxDamage(64)));

	public static final Item SORTING_WAND = register("sorting_wand",
			new UtilityTools.SortingWand(new Item.Settings().maxCount(1)));

	public static final Item GRAPPLING_HOOK = register("grappling_hook",
			new UtilityTools.GrapplingHook(new Item.Settings().maxCount(1).maxDamage(256)));

	public static final Item PANIC_BUTTON = register("panic_button",
			new UtilityTools.PanicButton(new Item.Settings().maxCount(1)));

	// ---------------------------------------------------------------- travel and charms

	public static final Item POCKET_BOAT = register("pocket_boat",
			new TravelTools.PocketBoat(new Item.Settings().maxCount(1)));

	public static final Item SPEED_SKATES = register("speed_skates",
			new TravelTools.SpeedSkates(new Item.Settings().maxCount(1)));

	public static final Item UPDRAFT_CHARM = register("updraft_charm",
			new TravelTools.UpdraftCharm(new Item.Settings().maxCount(16)));

	public static final Item SLIME_BOOTS = register("slime_boots",
			new TravelTools.SlimeBoots(new Item.Settings().maxCount(1)));

	public static final Item PARTY_HORN = register("party_horn",
			new CharmTools.PartyHorn(new Item.Settings().maxCount(1)));

	public static final Item FISHERS_CHARM = register("fishers_charm",
			new CharmTools.FishersCharm(new Item.Settings().maxCount(1)));

	public static final Item SHIELD_CHARGER = register("shield_charger",
			new CharmTools.ShieldCharger(new Item.Settings().maxCount(1)));

	public static final Item BIOME_COMPASS = register("biome_compass",
			new CharmTools.BiomeCompass(new Item.Settings().maxCount(1)));

	public static final Item VOID_FILTER = register("void_filter",
			new CharmTools.VoidFilter(new Item.Settings().maxCount(1)));

	public static final Item MOB_REPELLENT = register("mob_repellent",
			new CharmTools.MobRepellent(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	// ---------------------------------------------------------------- finding and herding

	public static final Item ORE_COMPASS = register("ore_compass",
			new MoreTools.OreCompass(new Item.Settings().maxCount(1)));

	public static final Item RESTOCK_WAND = register("restock_wand",
			new MoreTools.RestockWand(new Item.Settings().maxCount(1)));

	public static final Item CHISEL = register("chisel",
			new MoreTools.Chisel(new Item.Settings().maxCount(1).maxDamage(512)));

	public static final Item BREEDING_WHISTLE = register("breeding_whistle",
			new MoreTools.BreedingWhistle(new Item.Settings().maxCount(1)));

	public static final Item DECOY = register("decoy",
			new MoreTools.Decoy(new Item.Settings().maxCount(8)));

	// ---------------------------------------------------------------- quiver, belts and tethers

	public static final Item QUIVER = register("quiver",
			new FinalTools.Quiver(new Item.Settings().maxCount(1)));

	public static final Item TOTEM_BELT = register("totem_belt",
			new FinalTools.TotemBelt(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item ARROW_RECOVERY_CHARM = register("arrow_recovery_charm",
			new FinalTools.ArrowRecoveryCharm(new Item.Settings().maxCount(1)));

	public static final Item HEAD_HUNTERS_CHARM = register("head_hunters_charm",
			new FinalTools.HeadHuntersCharm(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item LEASH_ANCHOR = register("leash_anchor",
			new FinalTools.LeashAnchor(new Item.Settings().maxCount(1)));

	public static final Item ENDER_TETHER = register("ender_tether",
			new FinalTools.EnderTether(new Item.Settings().maxCount(16)));

	public static final Item SCAFFOLD_WAND = register("scaffold_wand",
			new FinalTools.ScaffoldWand(new Item.Settings().maxCount(1)));

	public static final Item CONCRETE_FLOODER = register("concrete_flooder",
			new FinalTools.ConcreteFlooder(new Item.Settings().maxCount(1)));

	public static final Item PLUMB_BOB = register("plumb_bob",
			new FinalTools.PlumbBob(new Item.Settings().maxCount(1)));

	public static final Item PORTABLE_ARMY = register("portable_army",
			new FinalTools.PortableArmy(new Item.Settings().maxCount(8).rarity(Rarity.RARE)));

	// ---------------------------------------------------------------- placeable blocks, part two

	public static final Item VACUUM_CHEST = register("vacuum_chest", ModBlocks.createBlockItem(ModBlocks.VACUUM_CHEST));

	public static final Item MAIL_BOX = register("mail_box", ModBlocks.createBlockItem(ModBlocks.MAIL_BOX));

	// ---------------------------------------------------------------- gadgets

	public static final Item LIGHT_METER = register("light_meter",
			new Gadgets.LightMeter(new Item.Settings().maxCount(1)));

	public static final Item CHUNK_BORDER_STICK = register("chunk_border_stick",
			new Gadgets.ChunkBorderStick(new Item.Settings().maxCount(1)));

	public static final Item MOB_CENSUS = register("mob_census",
			new Gadgets.MobCensus(new Item.Settings().maxCount(1)));

	public static final Item TIDE_CLOCK = register("tide_clock",
			new Gadgets.TideClock(new Item.Settings().maxCount(1)));

	public static final Item SPAWNER_READER = register("spawner_reader",
			new Gadgets.SpawnerReader(new Item.Settings().maxCount(1)));

	public static final Item DURABILITY_ALARM = register("durability_alarm",
			new Gadgets.DurabilityAlarm(new Item.Settings().maxCount(1)));

	public static final Item ITEM_FINDER = register("item_finder",
			new ItemFinderItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON), false));

	public static final Item ENDER_ITEM_FINDER = register("ender_item_finder",
			new ItemFinderItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), true));

	// ---------------------------------------------------------------- more items that look useful

	public static final Item RUBBER_DUCK = register("rubber_duck",
			new MoreJokeItems.RubberDuck(new Item.Settings().maxCount(16)));

	public static final Item MOTIVATIONAL_POSTER = register("motivational_poster",
			new MoreJokeItems.MotivationalPoster(new Item.Settings().maxCount(1)));

	public static final Item BROKEN_COMPASS = register("broken_compass",
			new MoreJokeItems.BrokenCompass(new Item.Settings().maxCount(1)));

	public static final Item AIRHORN = register("airhorn",
			new AirhornItem(new Item.Settings().maxCount(1), AirhornItem.Tier.NORMAL));

	public static final Item LOUD_AIRHORN = register("loud_airhorn",
			new AirhornItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON), AirhornItem.Tier.LOUD));

	public static final Item SUPER_AIRHORN = register("super_airhorn",
			new AirhornItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), AirhornItem.Tier.SUPER));

	public static final Item SELF_DESTRUCT_BUTTON = register("self_destruct_button",
			new MoreJokeItems.SelfDestructButton(new Item.Settings().maxCount(1)));

	public static final Item MYSTERY_STEW = register("mystery_stew",
			new MoreJokeItems.MysteryStew(new Item.Settings()
					.maxCount(8)
					.food(new FoodComponent.Builder().nutrition(6).saturationModifier(0.6F).build())));

	public static final Item BAG_OF_HOLDING = register("bag_of_holding",
			new MoreJokeItems.EmptyBagOfHolding(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	// ---------------------------------------------------------------- soul ties

	public static final Item SOUL_TIE = register("soul_tie",
			new SoulTieItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item BROKEN_SOUL_TIE = register("broken_soul_tie",
			new SoulTieItem.Broken(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	// ---------------------------------------------------------------- messing with friends

	public static final Item BOOP_GLOVE = register("boop_glove",
			new PrankItems.BoopGlove(new Item.Settings().maxCount(1)));

	public static final Item CONFETTI_CANNON = register("confetti_cannon",
			new PrankItems.ConfettiCannon(new Item.Settings().maxCount(1)));

	public static final Item SCREAMING_SHEEP = register("screaming_sheep",
			new PrankItems.ScreamingSheep(new Item.Settings().maxCount(16)));

	public static final Item HOT_POTATO = register("hot_potato",
			new PrankItems.HotPotato(new Item.Settings().maxCount(1)));

	public static final Item FAKE_DIAMOND = register("fake_diamond",
			new PrankItems.FakeDiamond(new Item.Settings().maxCount(64)));

	// ---------------------------------------------------------------- safety equipment

	public static final Item BUBBLE_SUIT = register("bubble_suit",
			new GagItems.BubbleSuit(new Item.Settings().maxCount(1).maxDamage(0)));

	public static final Item EMERGENCY_PARACHUTE = register("emergency_parachute",
			new GagItems.EmergencyParachute(new Item.Settings().maxCount(1)));

	public static final Item FIRE_EXTINGUISHER = register("fire_extinguisher",
			new ExtinguisherItem(new Item.Settings().maxCount(1), ExtinguisherItem.Mode.HELPFUL));

	public static final Item EVIL_FIRE_EXTINGUISHER = register("evil_fire_extinguisher",
			new ExtinguisherItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON), ExtinguisherItem.Mode.EVIL));

	public static final Item SUPER_EVIL_FIRE_EXTINGUISHER = register("super_evil_fire_extinguisher",
			new ExtinguisherItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), ExtinguisherItem.Mode.SUPER_EVIL));

	public static final Item NOISE_CANCELLING_HEADPHONES = register("noise_cancelling_headphones",
			new GagItems.NoiseCancellingHeadphones(new Item.Settings().maxCount(1)));

	public static final Item EMERGENCY_EXIT = register("emergency_exit",
			new GagItems.EmergencyExit(new Item.Settings().maxCount(1)));

	public static final Item LUCKY_COIN = register("lucky_coin",
			new MoreGagItems.LuckyCoin(new Item.Settings().maxCount(1)));

	public static final Item EMERGENCY_RATIONS = register("emergency_rations",
			new MoreGagItems.EmergencyRations(new Item.Settings()
					.maxCount(16)
					.food(new FoodComponent.Builder().nutrition(1).saturationModifier(0.0F).alwaysEdible().build())));

	// ---------------------------------------------------------------- the armoury

	public static final Item PISTOL = register("pistol",
			new GunItems.Pistol(new Item.Settings().maxCount(1)));

	public static final Item ASSAULT_RIFLE = register("assault_rifle",
			new GunItems.AssaultRifle(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item RPG = register("rpg",
			new GunItems.Rpg(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	// ---------------------------------------------------------------- the rest of the armoury

	public static final Item SMG = register("smg",
			new MoreGuns.Smg(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item SHOTGUN = register("shotgun",
			new MoreGuns.Shotgun(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item REVOLVER = register("revolver",
			new MoreGuns.Revolver(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final Item SNIPER = register("sniper",
			new MoreGuns.Sniper(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item MINIGUN = register("minigun",
			new MoreGuns.Minigun(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item FLAMETHROWER = register("flamethrower",
			new MoreGuns.Flamethrower(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item LASER_RIFLE = register("laser_rifle",
			new MoreGuns.LaserRifle(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item GRENADE_LAUNCHER = register("grenade_launcher",
			new MoreGuns.GrenadeLauncher(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item REDSTONE_RPG = register("redstone_rpg",
			new MoreGuns.RedstoneRpg(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item REAL_SELF_DESTRUCT_BUTTON = register("real_self_destruct_button",
			new RealSelfDestructButtonItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item RAIL_GUN = register("rail_gun",
			new WeaponItems.RailGun(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item SWORD_OF_LONG_ARMS = register("sword_of_long_arms",
			new WeaponItems.SwordOfLongArms(new Item.Settings()
					.maxCount(1)
					.rarity(Rarity.EPIC)
					.attributeModifiers(longArmsAttributes())));

	public static final Item BUCKET_OF_GOON = register("bucket_of_goon",
			new WeaponItems.BucketOfGoon(new Item.Settings()
					.maxCount(1)
					// Drinkable at any hunger level - it is not really food.
					.food(new FoodComponent.Builder().nutrition(0).saturationModifier(0.0F).alwaysEdible().build())));

	// ---------------------------------------------------------------- absurd storage

	public static final Item INFINITE_ROCKET = register("infinite_rocket",
			new BulkStorageItems.InfiniteRocket(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final Item INSANELY_LARGE_STORAGE = register("insanely_large_storage",
			new BulkStorageItems.InsanelyLargeStorage(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	// ---------------------------------------------------------------- diving

	public static final Item SCUBA_TANK = register("scuba_tank",
			new ScubaTankItem(new Item.Settings().maxCount(1).maxDamage(ScubaTankItem.CAPACITY)));

	// ---------------------------------------------------------------- admin toys

	public static final Item TRACTOR_BEAM = register("tractor_beam",
			new TractorBeamItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item REPULSOR_BEAM = register("repulsor_beam",
			new RepulsorBeamItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item THUNDER_STAFF = register("thunder_staff",
			new ThunderStaffItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item SWAPPER = register("swapper",
			new SwapperItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item YEET_STICK = register("yeet_stick",
			new YeetStickItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item INFINITE_FIREWORK = register("infinite_firework",
			new InfiniteFireworkItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item ADMIN_MAGNET = register("admin_magnet",
			new AdminMagnetItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	public static final Item MAGNETIC_REWIND = register("magnetic_rewind",
			new MagneticRewindItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

	private ModItems() {
	}

	/**
	 * The Sword of Long Arms' reach, as a real attribute so vanilla's own attack handling
	 * accepts the swing. Its damage rides along here too, since a sword built this way does
	 * not get the material's damage automatically.
	 */
	private static net.minecraft.component.type.AttributeModifiersComponent longArmsAttributes() {
		return net.minecraft.component.type.AttributeModifiersComponent.builder()
				.add(net.minecraft.entity.attribute.EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
						new net.minecraft.entity.attribute.EntityAttributeModifier(
								SlickFunMod.id("long_arms_reach"),
								com.slickfun.item.WeaponItems.SwordOfLongArms.REACH,
								net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE),
						net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
				.add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE,
						new net.minecraft.entity.attribute.EntityAttributeModifier(
								SlickFunMod.id("long_arms_damage"), 6.0D,
								net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE),
						net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
				.build();
	}

	private static Item.Settings portableSettings() {
		return new Item.Settings().maxCount(1);
	}

	private static Item register(String path, Item item) {
		Item registered = Registry.register(Registries.ITEM, SlickFunMod.id(path), item);
		ALL.add(registered);
		return registered;
	}

	private static Item registerPortable(String path, Item item) {
		Item registered = register(path, item);
		TOOLKIT_ORDER.add(registered);
		return registered;
	}

	/** Forces class initialisation so the static fields above actually run. */
	public static void register() {
		// Appended rather than declared in place: TOOLKIT_ORDER positions are the knife's
		// slot indices, and the hot tub is registered before the workstations. Slotting it
		// in at its declaration point would shift every other tool down one and orphan the
		// contents of every knife already in the world.
		TOOLKIT_ORDER.add(PORTABLE_HOT_TUB);
		TOOLKIT_ORDER.add(POCKET_BOAT);

		if (TOOLKIT_ORDER.size() > com.slickfun.util.Toolkit.SIZE) {
			throw new IllegalStateException("More portable utilities than Swiss Army Knife slots: " + TOOLKIT_ORDER.size());
		}

		SlickFunMod.LOGGER.info("Registered {} items ({} fit in the Swiss Army Knife).", ALL.size(), TOOLKIT_ORDER.size());
	}
}
