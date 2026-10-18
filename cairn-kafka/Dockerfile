# syntax=docker/dockerfile:1

FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -DskipTests package \
    && rm -f cairn-kafka/target/*.jar.original

FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S cairn && adduser -S -G cairn cairn
WORKDIR /app
COPY --from=build /build/cairn-kafka/target/*.jar app.jar
USER cairn
ENTRYPOINT ["java", "-jar", "app.jar"]
