# syntax=docker/dockerfile:1

# =============================================================================
# Moimyeon Backend (core:core-api) — deployment image
#
# - Multi-stage build (build stage separate from runtime stage)
# - Java 25 (matches gradle.properties javaVersion=25 / Spring Boot 4.1.0)
# - Builds the executable Spring Boot bootJar for the :core:core-api module
#   (NOT the Gradle plain jar, which is disabled for that module anyway)
# - Runtime image contains only a JRE + the extracted application layers
# - Runs as a non-root user
# - No secrets are baked in (DB / OAuth / JWT / AWS creds are injected at
#   runtime via environment variables — see core-api application.yml)
#
# Target runtime architecture is x86_64, so always build with:
#   docker buildx build --platform linux/amd64 ...
# =============================================================================


# -----------------------------------------------------------------------------
# Stage 1: build — compile and produce the executable bootJar, then explode it
#          into Spring Boot layers for better runtime-image layer caching.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copy the full multi-module project. .dockerignore keeps build outputs,
# IDE files and secrets out of the build context.
COPY . .

# Build only the runnable web module's bootJar. Gradle resolves the required
# sibling modules automatically. Tests are skipped for the image build.
# A BuildKit cache mount keeps the Gradle dependency cache warm across builds.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew :core:core-api:bootJar --no-daemon -x test && \
    APP_JAR="$(find core/core-api/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" && \
    echo "Selected boot jar: ${APP_JAR}" && \
    cp "${APP_JAR}" /workspace/app.jar

# Explode the bootJar into layers (Spring Boot 4 'tools' jarmode).
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted


# -----------------------------------------------------------------------------
# Stage 2: runtime — JRE only, non-root, contains just the exploded app layers.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# Create a dedicated non-root user/group to run the application.
# IDs are auto-assigned to avoid colliding with users baked into the base image.
RUN groupadd --system app && \
    useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

# Reconstruct the thin-jar runtime layout. Spring Boot 4's `extract --layers`
# splits libraries into separate layer directories purely for caching, but the
# app.jar manifest expects them all in a single sibling `lib/` directory.
# Copy the most-stable layer (external dependencies) first, then the app's own
# module jars, then the launcher jar itself — so an app-only change reuses the
# large dependency layer from cache.
COPY --from=builder --chown=app:app /workspace/extracted/dependencies/lib/ ./lib/
COPY --from=builder --chown=app:app /workspace/extracted/application/lib/ ./lib/
COPY --from=builder --chown=app:app /workspace/extracted/application/app.jar ./app.jar

USER app

# Spring Boot default port (no server.port override in the project config).
EXPOSE 8080

# Container-aware JVM defaults; override at deploy time as needed.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# NOTE: the active profile is intentionally NOT baked in. Provide it at runtime,
# e.g. `-e SPRING_PROFILES_ACTIVE=dev`. Health endpoint is GET /health.
ENTRYPOINT ["java", "-jar", "app.jar"]
