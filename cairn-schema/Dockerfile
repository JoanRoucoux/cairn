# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY . .
# LIQUIBASE_PASSWORD defaults to the local dev password and must be overridden in production.
ENTRYPOINT ["sh", "-c", "mvn -B -ntp liquibase:update -pl cairn-schema \
            -Dliquibase.url=jdbc:postgresql://postgres:5432/app \
            -Dliquibase.username=${LIQUIBASE_USERNAME:-app} \
            -Dliquibase.password=${LIQUIBASE_PASSWORD:-app}"]
