package com.slickfun.update;

/**
 * Compares version strings like {@code 1.14.0}.
 *
 * <p>Compared piece by piece as numbers, not as text, because a plain string comparison puts
 * {@code 1.9.0} above {@code 1.14.0} and the updater would refuse every release past the ninth.
 */
public final class Versions {
	private Versions() {
	}

	public static boolean isNewer(String candidate, String running) {
		if (candidate == null || running == null) {
			return false;
		}

		String[] left = clean(candidate).split("\\.");
		String[] right = clean(running).split("\\.");
		int length = Math.max(left.length, right.length);

		for (int part = 0; part < length; part++) {
			int a = numberAt(left, part);
			int b = numberAt(right, part);

			if (a != b) {
				return a > b;
			}
		}

		return false;
	}

	private static String clean(String version) {
		// Drop anything after a build suffix, e.g. "1.14.0+fabric".
		int cut = version.indexOf('+');
		return (cut < 0 ? version : version.substring(0, cut)).trim();
	}

	private static int numberAt(String[] parts, int index) {
		if (index >= parts.length) {
			return 0;
		}

		try {
			return Integer.parseInt(parts[index].replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
