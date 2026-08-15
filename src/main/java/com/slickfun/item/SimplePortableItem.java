package com.slickfun.item;

import java.util.function.Consumer;

import com.slickfun.util.ToolHost;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A pocket workstation that does nothing but open a screen: crafting table, anvil, smithing
 * table, and friends. Anything with state of its own gets a class instead.
 */
public class SimplePortableItem extends PortableUtilityItem {
	private final String tooltipKey;
	private final Consumer<ServerPlayerEntity> opener;

	public SimplePortableItem(Settings settings, String tooltipKey, Consumer<ServerPlayerEntity> opener) {
		super(settings);
		this.tooltipKey = tooltipKey;
		this.opener = opener;
	}

	@Override
	protected String tooltipKey() {
		return tooltipKey;
	}

	@Override
	public void openFor(ServerPlayerEntity player, ToolHost host) {
		opener.accept(player);
	}
}
