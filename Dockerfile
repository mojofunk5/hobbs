# Single-stage now - the jar arrives pre-built (see build.yml's "build" job, which runs shadowJar
# itself and uploads the result). This used to be a two-stage build (a JDK stage compiling the jar,
# then this JRE stage copying it out) so the runtime image wouldn't ship a JDK it doesn't need - but
# building inside Docker meant every deploy recompiled from scratch in a fresh, cache-less container,
# unable to reuse the Gradle build cache the "build" job's own separate Gradle invocation already
# populates. Measured at ~31s of a ~60s deploy, unaffected by the earlier dependency-layer-caching fix
# (that fixed the download portion, which was never the actual bottleneck). Building the jar once, in
# the job that already has a warm cache, and just packaging it here instead is the real fix - see
# docs/CI_PERFORMANCE.md.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/hobbs-0.0.1-SNAPSHOT-all.jar app.jar
# Baked in at build time so GET /version reflects the actual image running, regardless of how it's
# deployed - not passed as a docker-compose runtime env var, which would depend on the deploy script
# wiring it through correctly every time.
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA
# Sized to run comfortably inside a memory-limited container (see docker-compose.yml's mem_limit) -
# the VPS is shared with other services and this app's actual load is light. JAVA_TOOL_OPTIONS is
# read directly by the java launcher itself, so it works with the exec-form ENTRYPOINT below
# unchanged - no shell wrapping, which would break clean SIGTERM shutdown (java would no longer be
# PID 1).
# -Xms/-Xmx pinned equal to skip heap-resize churn, at 128m per Andy's own prior experience running
# Javalin apps comfortably in that much heap. SerialGC and single-tier JIT trade peak throughput this
# app doesn't need for a smaller footprint than G1/C2 default to. Metaspace is deliberately NOT capped
# with -XX:MaxMetaspaceSize - a first attempt at capping it tightly (48m) crashed under real request
# traffic with a genuine OutOfMemoryError: Metaspace (this app's classloading footprint - jOOQ,
# Jackson, Jetty, the Postgres driver - only fully materializes once real requests start hitting
# handlers, not at idle boot, so an idle-only smoke test won't catch an undersized cap). Rather than
# hand-pick another specific number, metaspace is left to grow as needed - the container's own
# mem_limit is the real backstop against unbounded growth (e.g. a genuine classloader leak), not a
# second, separately-guessed per-region cap.
# GC logging (unified JVM logging, not routed through Logback - a separate JVM-native subsystem)
# writes into the same persisted /app/logs volume as the app's own logs, so it survives container
# recreation the same way and is available for post-mortem debugging after a crash/OOM, not just
# whatever happened to still be in the container's now-gone stdout buffer. Its own file-based
# rotation (filecount/filesize), independent of logback.xml's - GC log lines are compact under
# SerialGC, so 5x10MB is generous for this app's traffic.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=24m -XX:MaxDirectMemorySize=8m -Xss256k -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M"
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "app.jar", "com.bonney.hobbs.HobbsApplication"]
CMD ["8080"]
