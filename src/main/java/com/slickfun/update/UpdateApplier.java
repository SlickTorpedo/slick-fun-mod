package com.slickfun.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.slickfun.SlickFunMod;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Swaps a staged download in, at the earliest moment the game gives us.
 *
 * <p>This runs before mods are loaded, which is the only point where the old jar has any
 * chance of being deletable. A running JVM holds its own jar open, so the swap can never
 * happen while playing - hence a restart.
 *
 * <p>The order matters more than anything else here. The old jar is deleted <em>first</em>,
 * and the new one is only renamed into place once that has definitely succeeded. Doing it the
 * other way round would, on any failure, leave two jars with the same mod id in the folder,
 * which Fabric refuses to launch with at all. A staged update that never applies is a minor
 * annoyance; a game that will not start is not.
 */
public class UpdateApplier implements PreLaunchEntrypoint {
	/** Downloads wear this so Fabric ignores them until they are deliberately applied. */
	public static final String SUFFIX = ".update";

	private static final String PREFIX = "slick-fun-mod-";

	@Override
	public void onPreLaunch() {
		try {
			apply();
		} catch (Exception e) {
			// Never let an update problem stop the game from starting.
			SlickFunMod.LOGGER.warn("Could not apply a staged update: {}", e.toString());
		}
	}

	private void apply() throws IOException {
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");

		if (!Files.isDirectory(mods)) {
			return;
		}

		List<Path> staged = new ArrayList<>();

		try (Stream<Path> files = Files.list(mods)) {
			files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).forEach(staged::add);
		}

		if (staged.isEmpty()) {
			return;
		}

		// Newest staged file wins if somehow more than one is waiting.
		staged.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
		Path newest = staged.get(0);
		String finalName = stripSuffix(newest.getFileName().toString());

		if (!removeOldJars(mods, finalName)) {
			SlickFunMod.LOGGER.warn("Update {} is downloaded but the running jar could not be removed. "
					+ "Delete the old slick-fun-mod jar by hand and restart.", finalName);
			return;
		}

		Files.move(newest, mods.resolve(finalName), StandardCopyOption.REPLACE_EXISTING);
		SlickFunMod.LOGGER.info("Applied update: {}", finalName);

		// Anything else still staged is now stale.
		for (int i = 1; i < staged.size(); i++) {
			Files.deleteIfExists(staged.get(i));
		}
	}

	/** @return true only if every older jar is genuinely gone from the folder. */
	private boolean removeOldJars(Path mods, String keeping) throws IOException {
		List<Path> old = new ArrayList<>();

		try (Stream<Path> files = Files.list(mods)) {
			files.filter(path -> {
				String name = path.getFileName().toString();
				return name.startsWith(PREFIX) && name.endsWith(".jar") && !name.equals(keeping);
			}).forEach(old::add);
		}

		boolean allGone = true;

		for (Path jar : old) {
			try {
				Files.delete(jar);
				SlickFunMod.LOGGER.info("Removed old build: {}", jar.getFileName());
			} catch (IOException e) {
				// Locked by this very JVM on Windows. Leave everything as it was.
				allGone = false;
			}
		}

		return allGone;
	}

	private static String stripSuffix(String name) {
		return name.substring(0, name.length() - SUFFIX.length());
	}
}
