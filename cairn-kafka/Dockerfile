# syntax=docker/dockerfile:1

FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -DskipTests package \
    && rm -f cairn-kafka/target/*.jar.original

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /build/cairn-kafka/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
