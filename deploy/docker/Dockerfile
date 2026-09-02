# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl "${MODULE}" -am package -DskipTests \
    && cp "${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar" /tmp/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /nonexistent octopus
WORKDIR /app
COPY --from=build /tmp/app.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

