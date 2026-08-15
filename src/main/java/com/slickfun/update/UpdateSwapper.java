package com.slickfun.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.slickfun.SlickFunMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies a staged update after the game has closed.
 *
 * <p>This exists because the obvious approach does not work. A running JVM holds its own mod
 * jar open, and on Windows an open file can be neither deleted nor renamed - both were tested
 * and both throw. {@link UpdateApplier} therefore refuses to swap and leaves the download
 * sitting there forever, which is precisely the "it downloaded but never installed" symptom.
 *
 * <p>So the swap is handed to a small script that waits for this process to exit and then does
 * the file moves itself. It is written next to the jars where it can be read, it is only
 * created when an update is genuinely waiting, and it deletes itself when finished.
 *
 * <p>Nothing is downloaded or decided here; the script only moves files the updater has
 * already fetched and verified.
 */
public final class UpdateSwapper {
	private static final String PREFIX = "slick-fun-mod-";
	private static final String SCRIPT_STEM = "slickfun-apply-update";

	private UpdateSwapper() {
	}

	public static void register() {
		// A shutdown hook rather than a game event, so it covers a client closing from the
		// menu, a server stopping, and a window closed with the X.
		Runtime.getRuntime().addShutdownHook(new Thread(UpdateSwapper::runQuietly, "slickfun-update-swap"));
	}

	private static void runQuietly() {
		try {
			run();
		} catch (Exception e) {
			SlickFunMod.LOGGER.warn("Could not hand off the update: {}", e.toString());
		}
	}

	private static void run() throws IOException {
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");

		if (!Files.isDirectory(mods)) {
			return;
		}

		Path staged = newestStaged(mods);

		if (staged == null) {
			return;
		}

		String finalName = staged.getFileName().toString();
		finalName = finalName.substring(0, finalName.length() - UpdateApplier.SUFFIX.length());

		List<Path> old = oldJars(mods, finalName);
		Path script = mods.resolve(SCRIPT_STEM + (isWindows() ? ".bat" : ".sh"));

		Files.writeString(script, isWindows()
				? windowsScript(old, staged, mods.resolve(finalName))
				: unixScript(old, staged, mods.resolve(finalName)));

		launch(script);
		SlickFunMod.LOGGER.info("Update {} handed to the installer.", finalName);
	}

	private static Path newestStaged(Path mods) throws IOException {
		List<Path> staged = new ArrayList<>();

		try (Stream<Path> files = Files.list(mods)) {
			files.filter(path -> path.getFileName().toString().endsWith(UpdateApplier.SUFFIX)).forEach(staged::add);
		}

		staged.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
		return staged.isEmpty() ? null : staged.get(0);
	}

	private static List<Path> oldJars(Path mods, String keeping) throws IOException {
		List<Path> old = new ArrayList<>();

		try (Stream<Path> files = Files.list(mods)) {
			files.filter(path -> {
				String name = path.getFileName().toString();
				return name.startsWith(PREFIX) && name.endsWith(".jar") && !name.equals(keeping);
			}).forEach(old::add);
		}

		return old;
	}

	/**
	 * Retries the delete, because the game takes a moment to release the file after this hook
	 * runs. The new jar is only moved into place once the old one is genuinely gone - the same
	 * ordering rule as everywhere else, since two jars of one mod id will not launch.
	 */
	private static String windowsScript(List<Path> old, Path staged, Path target) {
		StringBuilder script = new StringBuilder();
		script.append("@echo off\r\n");
		script.append("setlocal enableextensions\r\n");

		for (Path jar : old) {
			script.append("set TRIES=0\r\n");
			script.append(":retry_").append(safeLabel(jar)).append("\r\n");
			script.append("del /f /q \"").append(jar).append("\" >nul 2>&1\r\n");
			script.append("if not exist \"").append(jar).append("\" goto done_").append(safeLabel(jar)).append("\r\n");
			script.append("set /a TRIES+=1\r\n");
			script.append("if %TRIES% GEQ 30 goto giveup\r\n");
			script.append("ping -n 2 127.0.0.1 >nul\r\n");
			script.append("goto retry_").append(safeLabel(jar)).append("\r\n");
			script.append(":done_").append(safeLabel(jar)).append("\r\n");
		}

		script.append("move /y \"").append(staged).append("\" \"").append(target).append("\" >nul 2>&1\r\n");
		script.append(":giveup\r\n");
		script.append("del /f /q \"%~f0\" >nul 2>&1\r\n");
		return script.toString();
	}

	private static String unixScript(List<Path> old, Path staged, Path target) {
		StringBuilder script = new StringBuilder("#!/bin/sh\n");

		for (Path jar : old) {
			script.append("for i in $(seq 1 30); do rm -f \"").append(jar)
					.append("\" 2>/dev/null; [ ! -e \"").append(jar).append("\" ] && break; sleep 1; done\n");
			script.append("[ -e \"").append(jar).append("\" ] && exit 0\n");
		}

		script.append("mv -f \"").append(staged).append("\" \"").append(target).append("\"\n");
		script.append("rm -f \"$0\"\n");
		return script.toString();
	}

	private static void launch(Path script) throws IOException {
		ProcessBuilder builder = isWindows()
				? new ProcessBuilder("cmd", "/c", "start", "/min", "", script.toString())
				: new ProcessBuilder("sh", script.toString());

		builder.directory(script.getParent().toFile());
		// Detached: it has to outlive the process that started it.
		builder.start();
	}

	private static String safeLabel(Path jar) {
		return jar.getFileName().toString().replaceAll("[^A-Za-z0-9]", "_");
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
