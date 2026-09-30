# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
# G1 explicitly: with 2 CPUs and a small memory limit the JVM falls back to
# single-threaded Serial GC, which paused for seconds at a time under load.
# Heap at half the container: native memory (thread stacks, malloc arenas,
# network buffers) grows with connections and needs the rest. At 75% the
# kernel OOM-killed the app before Java ever saw its own heap run out.
ENV MALLOC_ARENA_MAX=2
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=50", "-XX:MaxDirectMemorySize=256m", "-jar", "/app/app.jar"]
