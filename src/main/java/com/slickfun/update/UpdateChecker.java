package com.slickfun.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slickfun.SlickFunMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Watches the mod's own repository and fetches new builds.
 *
 * <p>Downloads land beside the mod as {@code <name>.jar.update}, never as a loadable jar. That
 * suffix is the whole safety design: Fabric only loads {@code .jar}, so a finished download, a
 * half-finished one, or one that never gets applied are all equally harmless. Two jars of the
 * same mod id in the folder is a hard launch failure, and this must never be able to cause one.
 *
 * <p>The swap itself happens in {@link UpdateApplier} at startup, because a running JVM holds
 * its own jar open and Windows will not delete a file that is in use.
 *
 * <p>Everything here runs on its own daemon thread. Network calls must never touch the server
 * thread - a slow response would otherwise stall the whole game.
 */
public final class UpdateChecker {
	private static final String REPO = "https://raw.githubusercontent.com/SlickTorpedo/slick-fun-mod/main/releases/";
	private static final String MANIFEST = REPO + "latest.json";

	private static final Duration INTERVAL = Duration.ofMinutes(2);
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private static ScheduledExecutorService scheduler;
	private static String downloaded;

	private UpdateChecker() {
	}

	public static void register() {
		if (!UpdateConfig.enabled()) {
			SlickFunMod.LOGGER.info("Auto-update is switched off in config/slickfun.properties.");
			return;
		}

		scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "slickfun-update-check");
			// Daemon, so a pending check can never hold the game open on shutdown.
			thread.setDaemon(true);
			return thread;
		});

		scheduler.scheduleWithFixedDelay(UpdateChecker::checkQuietly,
				10, INTERVAL.toSeconds(), TimeUnit.SECONDS);

		SlickFunMod.LOGGER.info("Auto-update watching {} every {} minutes.", MANIFEST, INTERVAL.toMinutes());
	}

	private static void checkQuietly() {
		try {
			check();
		} catch (Exception e) {
			// A repo that is unreachable, rate limited or mid-push is not worth a stack trace
			// every two minutes; it will simply try again.
			SlickFunMod.LOGGER.debug("Update check failed: {}", e.toString());
		}
	}

	private static void check() throws IOException, InterruptedException {
		String running = currentVersion();
		JsonObject manifest = fetchManifest();

		String latest = manifest.get("version").getAsString();
		String file = manifest.get("file").getAsString();

		if (!Versions.isNewer(latest, running)) {
			return;
		}

		if (latest.equals(downloaded)) {
			// Already sitting in the folder waiting for a restart.
			return;
		}

		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
		Path staged = mods.resolve(file + UpdateApplier.SUFFIX);

		if (Files.exists(staged)) {
			downloaded = latest;
			return;
		}

		download(REPO + file, staged);
		downloaded = latest;

		SlickFunMod.LOGGER.info("Downloaded Slick Fun Mod {} - it will be applied on the next start.", latest);
		UpdateNotifier.announce(latest);
	}

	private static JsonObject fetchManifest() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(MANIFEST))
				.timeout(TIMEOUT)
				.header("User-Agent", "slick-fun-mod-updater")
				// Raw GitHub caches hard; without this a new release can take minutes to show.
				.header("Cache-Control", "no-cache")
				.GET()
				.build();

		HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			throw new IOException("manifest returned " + response.statusCode());
		}

		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	/**
	 * Fetches to a temporary file and moves it into place only once it is complete, so a
	 * dropped connection can never leave a truncated jar staged for install.
	 */
	private static void download(String url, Path target) throws IOException, InterruptedException {
		Path partial = target.resolveSibling(target.getFileName() + ".partial");
		Files.createDirectories(target.getParent());

		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(TIMEOUT)
				.header("User-Agent", "slick-fun-mod-updater")
				.GET()
				.build();

		HttpResponse<Path> response = client().send(request, HttpResponse.BodyHandlers.ofFile(partial));

		if (response.statusCode() != 200) {
			Files.deleteIfExists(partial);
			throw new IOException("download returned " + response.statusCode());
		}

		if (Files.size(partial) == 0) {
			Files.deleteIfExists(partial);
			throw new IOException("download was empty");
		}

		Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private static HttpClient client() {
		return HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(SlickFunMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}
}
