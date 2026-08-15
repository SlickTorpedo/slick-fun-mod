package com.slickfun.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.slickfun.SlickFunMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The one switch for the updater, in {@code config/slickfun.properties}.
 *
 * <p>Anything that downloads code and installs it should be turnable off without editing the
 * mod, so the file is written on first run with the setting spelled out.
 */
public final class UpdateConfig {
	private static final String FILE = "slickfun.properties";
	private static final String KEY = "auto_update";

	private UpdateConfig() {
	}

	public static boolean enabled() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		Properties properties = new Properties();

		try {
			if (Files.exists(path)) {
				try (var in = Files.newBufferedReader(path)) {
					properties.load(in);
				}
			} else {
				write(path);
				return true;
			}
		} catch (IOException e) {
			SlickFunMod.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
			return true;
		}

		return Boolean.parseBoolean(properties.getProperty(KEY, "true"));
	}

	private static void write(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, """
				# Slick Fun Mod

				# Check github.com/SlickTorpedo/slick-fun-mod every two minutes for a new build,
				# download it, and install it the next time the game starts.
				# Set to false to never contact the internet.
				auto_update=true
				""");
	}
}
