# =============================================================================
# Multi-stage Dockerfile for portfolio-tracker
#
# Stage 1 (builder) — compiles the application and produces a fat JAR.
# Stage 2 (runtime) — copies only the JAR into a minimal JRE image.
#
# WHY multi-stage?
#   The Maven build requires the JDK, Maven, and all compile-time dependencies
#   (~300 MB).  None of that is needed at runtime.  Multi-stage keeps the final
#   image small and free of build tooling (reducing attack surface).
# =============================================================================

# ── Stage 1: build ────────────────────────────────────────────────────────────
# eclipse-temurin is the official OpenJDK distribution recommended by the
# Docker Hub Java guidelines.  Using a named tag (25-jdk-alpine) pins the
# JDK major version while accepting patch-level security updates.
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /workspace

# Copy the Maven wrapper and POM first — Docker layer-caches these.
# If only source code changes, Maven downloads are NOT re-fetched on rebuild.
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./

# Download all dependencies.  --no-transfer-progress suppresses the
# noisy download progress bars that bloat CI logs.
# This layer is cached as long as pom.xml doesn't change.
RUN chmod +x mvnw && ./mvnw dependency:go-offline --no-transfer-progress -q

# Now copy the full source.  This layer is only rebuilt when source changes.
COPY src src

# Package, skipping tests.
# Tests should have been run in a prior CI step (e.g. mvn test), not here.
# Including them would double the build time and require the H2 runtime dependency
# to be available inside the container build environment.
RUN ./mvnw package -DskipTests --no-transfer-progress -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
# JRE-only image — no compiler, no Maven, no source code.
# alpine keeps the image under ~200 MB.
FROM eclipse-temurin:25-jre-alpine AS runtime

# Run as a non-root user.
# Creating a dedicated user (not 'nobody') makes log attribution clearer and
# allows fine-grained file permission control if needed.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy the fat JAR from the builder stage.
# The wildcard handles version suffixes in the artifact name (e.g. -0.0.1-SNAPSHOT).
COPY --from=builder /workspace/target/portfolio-*.jar app.jar

# 8080 is the default Spring Boot port.
EXPOSE 8080

# ── Spring profile & JVM tuning ──────────────────────────────────────────────
# SPRING_PROFILES_ACTIVE defaults to 'prod'; override with -e at runtime.
# JAVA_OPTS lets operators inject JVM flags (heap size, GC tuning, etc.)
# without rebuilding the image, e.g.:
#   docker run -e JAVA_OPTS="-Xmx512m" ...
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

# Use exec form (JSON array) so the JVM process receives OS signals directly
# (e.g. SIGTERM from `docker stop`) and can shut down gracefully.
# Shell form wraps the command in /bin/sh -c, which traps signals itself and
# delays or swallows them — the JVM never sees SIGTERM.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

