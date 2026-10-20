# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-27 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -pl cairn-schema liquibase:help
ENTRYPOINT ["sh", "-c", "mvn -B -ntp -o liquibase:update -pl cairn-schema \
            -Dliquibase.url=jdbc:postgresql://postgres:5432/app \
            -Dliquibase.username=${LIQUIBASE_USERNAME:-app}"]
