package com.slickfun.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Run something a few ticks from now on the server thread. Used for staged sound effects
 * and for opening a screen after the click that asked for it has finished being handled.
 */
public final class ServerScheduler {
	private static final List<Scheduled> PENDING = new ArrayList<>();
	private static long tickCounter;

	private ServerScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	public static void schedule(int delayTicks, Runnable action) {
		PENDING.add(new Scheduled(tickCounter + Math.max(1, delayTicks), action));
	}

	private static void tick() {
		tickCounter++;

		if (PENDING.isEmpty()) {
			return;
		}

		List<Runnable> due = null;
		Iterator<Scheduled> iterator = PENDING.iterator();

		while (iterator.hasNext()) {
			Scheduled scheduled = iterator.next();

			if (scheduled.runAt <= tickCounter) {
				iterator.remove();

				if (due == null) {
					due = new ArrayList<>();
				}

				due.add(scheduled.action);
			}
		}

		// Run outside the iteration so a task may schedule more work.
		if (due != null) {
			due.forEach(Runnable::run);
		}
	}

	private record Scheduled(long runAt, Runnable action) {
	}
}
