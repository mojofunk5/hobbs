package com.bonney.hobbs.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Sweeps rows out of Postgres-backed state that would otherwise just grow forever (expired sessions,
// stale rate-limit buckets). Runs in-JVM rather than a separate cron container or Postgres extension -
// no new infrastructure for a hobby-scale app. Must be shut down alongside the app (see
// HobbsApplication.stop()) - otherwise every HobbsApplication instance the integration test suite
// constructs leaks its own background thread.
public class ScheduledCleanupJobs {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCleanupJobs.class);
    private static final int INTERVAL_MINUTES = 30;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "scheduled-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public void schedule(String name, Runnable job) {
        executor.scheduleAtFixedRate(() -> runSafely(name, job), INTERVAL_MINUTES, INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void runSafely(String name, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException e) {
            // A single failed cleanup pass (e.g. a transient DB hiccup) shouldn't cancel all future
            // runs - scheduleAtFixedRate stops scheduling entirely if the task throws.
            logger.warn("Scheduled cleanup job '{}' failed, will retry next interval", name, e);
        }
    }

    public void stop() {
        executor.shutdownNow();
    }
}
