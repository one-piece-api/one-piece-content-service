# syntax=docker/dockerfile:1
# Runtime-only image: the jar is built beforehand (./gradlew bootJar), not inside this
# Dockerfile - see scripts/build-image.sh for local dev. Same pattern as
# one-piece-user-service's Dockerfile (keeps GitHub Packages credentials one-piece-exception
# needs, see build.gradle.kts, confined to wherever Gradle actually runs).
# glibc base (Ubuntu), not Alpine: the WebP plugin bundles libwebp built for glibc only,
# and extracts it into /tmp at first use - see docs/adr/0010-webp-images.md.
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
