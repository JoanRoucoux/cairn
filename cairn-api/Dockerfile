# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -DskipTests package \
    && rm -f cairn-api/target/*.jar.original

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /build/cairn-api/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget --spider -q http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
