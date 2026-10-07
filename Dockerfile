# syntax=docker/dockerfile:1

# =============================================================================
# Moimyeon Backend deployment images
#
# - One Gradle build produces both executable bootJars
# - Named runtime targets: core-api and core-worker
# - Java 25 (matches gradle.properties javaVersion=25 / Spring Boot 4.1.0)
# - Runtime image contains only a JRE + the extracted application layers
# - Runs as a non-root user
# - No secrets are baked in; runtime configuration is injected by ECS
# - Each target ships a JDK AOT cache trained on the offline local profile
#
# Build a specific image with:
#   docker buildx build --platform linux/amd64 --target core-api ...
#   docker buildx build --platform linux/amd64 --target core-worker ...
# =============================================================================


# -----------------------------------------------------------------------------
# Stage 1: build both executable bootJars in one Gradle invocation.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew :core:core-api:bootJar :core:core-worker:bootJar --no-daemon -x test && \
    CORE_API_JAR="$(find core/core-api/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" && \
    CORE_WORKER_JAR="$(find core/core-worker/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" && \
    test -n "${CORE_API_JAR}" && \
    test -n "${CORE_WORKER_JAR}" && \
    cp "${CORE_API_JAR}" /workspace/core-api.jar && \
    cp "${CORE_WORKER_JAR}" /workspace/core-worker.jar


# -----------------------------------------------------------------------------
# Stage 2: extract Spring Boot layers independently for each runtime image.
# -----------------------------------------------------------------------------
FROM builder AS core-api-layers

RUN cp core-api.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract \
    --layers \
    --destination extracted/core-api

FROM builder AS core-worker-layers

RUN cp core-worker.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract \
    --layers \
    --destination extracted/core-worker


# -----------------------------------------------------------------------------
# Stage 3: shared JRE runtime boundary.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime-base

RUN groupadd --system app && \
    useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app && \
    mkdir -p /app && \
    chown app:app /app

WORKDIR /app

USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]


# -----------------------------------------------------------------------------
# Stage 4: deployable targets. Each image sees only its own application layers.
# -----------------------------------------------------------------------------
FROM runtime-base AS core-api

COPY --from=core-api-layers --chown=app:app /workspace/extracted/core-api/dependencies/lib/ ./lib/
COPY --from=core-api-layers --chown=app:app /workspace/extracted/core-api/application/lib/ ./lib/
COPY --from=core-api-layers --chown=app:app /workspace/extracted/core-api/application/app.jar ./app.jar

# JDK AOT cache (MOI-565): one training start with the self-contained local
# profile (in-memory H2, already on the runtime classpath) records loaded and
# linked classes, so ECS starts faster. Same JRE, jar and classpath as runtime;
# the values below are throwaway placeholders, never real credentials. A failed
# training run must not block a deploy: the JVM starts without a usable cache.
# CI BuildKit injects OTEL_* pointing at its unix trace socket, which Spring's
# OTLP exporter rejects, so the training run drops every OTEL_* variable.
RUN for name in $(env | sed -n 's/^\(OTEL_[A-Za-z0-9_]*\)=.*/\1/p'); do unset "${name}"; done && \
    JWT_SECRET=aot-training-placeholder-0123456789abcdef0123456789abcdef \
    GOOGLE_OAUTH_CLIENT_ID=aot-training-placeholder \
    GOOGLE_OAUTH_CLIENT_SECRET=aot-training-placeholder \
    java -XX:AOTCacheOutput=app.aot \
      -Dspring.context.exit=onRefresh \
      -Dspring.profiles.active=local \
      -jar app.jar \
    || echo "AOT training failed; this image starts without a full cache." >&2
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:AOTCache=app.aot"

EXPOSE 8080

FROM runtime-base AS core-worker

COPY --from=core-worker-layers --chown=app:app /workspace/extracted/core-worker/dependencies/lib/ ./lib/
COPY --from=core-worker-layers --chown=app:app /workspace/extracted/core-worker/application/lib/ ./lib/
COPY --from=core-worker-layers --chown=app:app /workspace/extracted/core-worker/application/app.jar ./app.jar

# Same placeholder properties as WorkerContextTest; the local profile has no
# env mapping for them.
RUN for name in $(env | sed -n 's/^\(OTEL_[A-Za-z0-9_]*\)=.*/\1/p'); do unset "${name}"; done && \
    JWT_SECRET=aot-training-placeholder-0123456789abcdef0123456789abcdef \
    java -XX:AOTCacheOutput=app.aot \
      -Dspring.context.exit=onRefresh \
      -Dspring.profiles.active=local \
      -Dnotification.email.ses.from-address=aot-training@example.invalid \
      -Dnotification.email.gmail.from-address=aot-training@example.invalid \
      -Dnotification.web-push.fcm.project-id=aot-training \
      -Dnotification.action-base-url=https://example.invalid \
      -jar app.jar \
    || echo "AOT training failed; this image starts without a full cache." >&2
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:AOTCache=app.aot"

# Keep `docker build .` backward-compatible with the Core API image.
FROM core-api AS runtime
