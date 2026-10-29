# syntax=docker/dockerfile:1

# --- Build stage: compile with the JDK and package the Spring Boot jar ---
# On the builder's own platform: the jar is the same for every architecture,
# so a multi-arch build (release.yml) does not compile under emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25.0.4_7-jdk-noble AS build
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

# --- Stockfish stage: the official release, pinned and checksum-verified ---
# The "universal" builds run on any CPU of their family (no avx2/bmi2 surprises).
# Same release as CI (.github/workflows/ci.yml); digests from the GitHub release.
FROM eclipse-temurin:25.0.4_7-jdk-noble AS stockfish
ARG TARGETARCH
ARG STOCKFISH_RELEASE=sf_19
ARG STOCKFISH_SHA256_AMD64=9defc0d4e55d49c65a6d042f3e571a39fcea499ade6dbe741b53b8c65e03611f
ARG STOCKFISH_SHA256_ARM64=fe26cfd1d9db4c8af3d21e24d9ff34cacb31c1f940085a7583da11796f2bac01
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN set -eu; \
    case "${TARGETARCH:-amd64}" in \
      amd64) asset=stockfish-linux-x86-64-universal.tar.gz; sha="$STOCKFISH_SHA256_AMD64" ;; \
      arm64) asset=stockfish-linux-arm64-universal.tar.gz; sha="$STOCKFISH_SHA256_ARM64" ;; \
      *) echo "No Stockfish build for ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fsSL --retry 3 -o /tmp/stockfish.tar.gz \
        "https://github.com/official-stockfish/Stockfish/releases/download/${STOCKFISH_RELEASE}/${asset}"; \
    echo "${sha}  /tmp/stockfish.tar.gz" | sha256sum -c -; \
    mkdir /tmp/stockfish; \
    tar -xzf /tmp/stockfish.tar.gz -C /tmp/stockfish; \
    binary=$(find /tmp/stockfish -type f -name 'stockfish-linux-*' ! -name '*.*' | head -n 1); \
    test -n "$binary"; \
    install -m 0755 "$binary" /usr/local/bin/stockfish; \
    echo quit | /usr/local/bin/stockfish > /dev/null

# --- Runtime stage: JRE only, non-root user ---
FROM eclipse-temurin:25.0.4_7-jre-noble
# OCI labels; the release workflow sets version, revision and creation date
# (without this "version", a local build would show the Ubuntu base's).
# "source" also links the package on GHCR to the repository.
LABEL org.opencontainers.image.title="Kopi Chess" \
      org.opencontainers.image.version="dev" \
      org.opencontainers.image.description="Play chess against five bots and review every game with Stockfish. Local only." \
      org.opencontainers.image.source="https://github.com/arquitechthor/kopi-chess" \
      org.opencontainers.image.url="https://github.com/arquitechthor/kopi-chess" \
      org.opencontainers.image.licenses="GPL-3.0-or-later"
RUN groupadd --system kopi && useradd --system --gid kopi --no-create-home kopi
COPY --from=stockfish /usr/local/bin/stockfish /usr/local/bin/stockfish
# Smoke test: the engine starts in this image too (libraries, architecture).
RUN echo quit | /usr/local/bin/stockfish > /dev/null
ENV KOPI_STOCKFISH=/usr/local/bin/stockfish
# Inside a container, every interface: what reaches it is decided by the
# published port (compose.yaml: 127.0.0.1 only).
ENV KOPI_ADDRESS=0.0.0.0
# Logs as JSON lines (Elastic Common Schema), one field per fact, gameId among them (design §14).
# Plain text instead: LOGGING_STRUCTURED_FORMAT_CONSOLE= (empty); with Compose, KOPI_LOG_FORMAT=.
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
WORKDIR /app
COPY --from=build /build/kopi-bootstrap/target/kopi-chess.jar app.jar
USER kopi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
