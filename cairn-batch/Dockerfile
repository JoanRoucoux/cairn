# syntax=docker/dockerfile:1

FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -DskipTests package \
    && rm -f cairn-batch/target/*.jar.original

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /build/cairn-batch/target/*.jar app.jar
# One-shot job: no HEALTHCHECK, it exits when the run completes.
ENTRYPOINT ["java", "-jar", "app.jar"]
