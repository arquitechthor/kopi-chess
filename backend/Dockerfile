# syntax=docker/dockerfile:1

# --- Build stage: compile with the JDK and package the Spring Boot jar ---
FROM eclipse-temurin:25.0.4_7-jdk-noble AS build
# mvnw needs unzip: without it, it falls back to the .tar.gz distribution and
# the pinned SHA-256 (of the .zip, also used by mvnw.cmd on Windows) fails.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY . .
# The Maven repository is cached between builds (BuildKit cache mount).
# Tests run in CI and locally with ./mvnw verify, not inside the image build.
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -ntp -DskipTests -pl kopi-bootstrap -am package

# --- Runtime stage: JRE only, non-root user ---
FROM eclipse-temurin:25.0.4_7-jre-noble
RUN groupadd --system kopi && useradd --system --gid kopi --no-create-home kopi
WORKDIR /app
COPY --from=build /build/kopi-bootstrap/target/kopi-chess.jar app.jar
USER kopi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
