package com.slickfun.update;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Tells operators when a build has been downloaded and is waiting for a restart.
 *
 * <p>The check runs off-thread, so the message is handed to the server to deliver rather than
 * being sent from wherever the download happened to finish.
 */
public final class UpdateNotifier {
	private static MinecraftServer server;
	private static String pending;

	private UpdateNotifier() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;

			if (pending != null) {
				broadcast(pending);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> server = null);
	}

	public static void announce(String version) {
		pending = version;

		MinecraftServer current = server;

		if (current != null) {
			current.execute(() -> broadcast(version));
		}
	}

	private static void broadcast(String version) {
		MinecraftServer current = server;

		if (current == null) {
			return;
		}

		Text message = Text.translatable("message.slickfun.update.ready", version).formatted(Formatting.AQUA);

		current.getPlayerManager().getPlayerList().stream()
				.filter(player -> player.hasPermissionLevel(2))
				.forEach(player -> player.sendMessage(message, false));
	}
}
