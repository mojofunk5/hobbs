FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# Copy only the dependency manifest first (build.gradle/settings.gradle/the wrapper), resolve
# dependencies against just that, and only *then* copy the rest of the source - the standard
# package-manager Docker caching pattern (same idea as COPY package.json before COPY . . in a Node
# image). This layer - the expensive one, downloading the Gradle distribution itself plus every
# dependency (including the jooqGenerator configuration, which `dependencies` resolves too since it
# reports every configuration by default) - only invalidates when these manifest files actually
# change, not on every commit, unlike the RUN below it.
#
# A previous version of this used `RUN --mount=type=cache,target=/root/.gradle` instead, which looked
# equivalent but wasn't: a cache *mount* is BuildKit-local state tied to the runner's own disk, not
# something build.yml's cache-to: type=gha exports (that only exports real image layers) - confirmed
# by watching Gradle re-download its own distribution from scratch on a run that should have been a
# cache hit. This version uses a genuine, exportable layer instead.
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY . .
RUN ./gradlew shadowJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/hobbs-0.0.1-SNAPSHOT-all.jar app.jar
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
