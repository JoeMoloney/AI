FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Buildscript + wrapper first, sources last, so the dependency-download layer
# stays cached when only sources change.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY src ./src

RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre

# curl is only needed for the image healthcheck (actuator /actuator/health)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home memory
USER memory
WORKDIR /app

# exactly one jar lands in build/libs (bootJar); the glob keeps this stable
# across version bumps
COPY --from=build --chown=memory /build/build/libs/*.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
