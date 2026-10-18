# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-27 AS build
WORKDIR /build
COPY . .
ENTRYPOINT ["sh", "-c", "mvn -B -ntp liquibase:update -pl cairn-schema \
            -Dliquibase.url=jdbc:postgresql://postgres:5432/app \
            -Dliquibase.username=${LIQUIBASE_USERNAME:-app}"]
