package com.slickfun.item;

import java.util.List;

import com.slickfun.SlickFunMod;
import com.slickfun.util.ServerScheduler;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * The food court. All three are ordinary food, so they are cleared by death the same way
 * every other status effect is - no bookkeeping needed.
 */
public class CostcoFoodItem extends Item {
	public enum Flavour {
		/** Sits like a brick: slower, and barely gets off the ground. */
		COOKIE("costco_cookie"),
		/** Absurdly good value, and it shows. */
		PIZZA("costco_pizza_slice"),
		/** Heavenly. Literally. */
		HOT_DOG("costco_hot_dog");

		private final String key;

		Flavour(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	private static final int THIRTY_SECONDS = 20 * 30;
	private static final int FIVE_SECONDS = 20 * 5;

	private static final Identifier HEAVY_MODIFIER_ID = SlickFunMod.id("costco_cookie_weight");

	private final Flavour flavour;

	public CostcoFoodItem(Settings settings, Flavour flavour) {
		super(settings);
		this.flavour = flavour;
	}

	public static FoodComponent foodComponent(int nutrition, float saturation) {
		return new FoodComponent.Builder().nutrition(nutrition).saturationModifier(saturation).build();
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		ItemStack result = super.finishUsing(stack, world, user);

		if (world.isClient) {
			return result;
		}

		switch (this.flavour) {
			case COOKIE -> {
				user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, THIRTY_SECONDS, 1, false, true, true));
				weighDown(user);
				world.playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.8F, 0.7F);
			}
			case PIZZA -> {
				user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, THIRTY_SECONDS, 1, false, true, true));
				user.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, THIRTY_SECONDS, 1, false, true, true));
			}
			case HOT_DOG -> {
				user.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, FIVE_SECONDS, 0, false, true, true));
				world.playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.6F);
			}
		}

		return result;
	}

	/**
	 * Cuts the eater's jump nearly in half for the duration.
	 *
	 * <p>The obvious approach - a negative Jump Boost level - silently does the opposite:
	 * {@code StatusEffectInstance} clamps its amplifier to 0-255, so a negative level becomes
	 * Jump Boost I and you end up jumping <em>higher</em>. Going straight at the underlying
	 * jump strength attribute is the only way to actually make someone heavier.
	 */
	private static void weighDown(LivingEntity user) {
		EntityAttributeInstance jump = user.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);

		if (jump == null) {
			return;
		}

		jump.removeModifier(HEAVY_MODIFIER_ID);
		jump.addTemporaryModifier(new EntityAttributeModifier(
				HEAVY_MODIFIER_ID, -0.45D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

		// Temporary modifiers are not saved, so dying clears it for free; this is just the
		// timer for people who survive the cookie.
		ServerScheduler.schedule(THIRTY_SECONDS, () -> {
			EntityAttributeInstance current = user.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);

			if (current != null) {
				current.removeModifier(HEAVY_MODIFIER_ID);
			}
		});
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.slickfun." + this.flavour.key()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.slickfun." + this.flavour.key() + ".2").formatted(Formatting.DARK_GRAY));
	}
}
