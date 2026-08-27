# syntax=docker/dockerfile:1
#
# One-shot migration image: applies the Liquibase changelog and exits. Never a long-lived
# server, so a single Maven stage is enough — no runtime stage to slim down.

FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
# cairn-schema/pom.xml points at localhost:5432 for local `./mvnw liquibase:update`; the
# properties below are the plugin's actual parameters, overridable from the command line, so
# this container instead targets the compose network's postgres service.
ENTRYPOINT ["mvn", "-B", "-ntp", "liquibase:update", "-pl", "cairn-schema", \
            "-Dliquibase.url=jdbc:postgresql://postgres:5432/app", \
            "-Dliquibase.username=app", \
            "-Dliquibase.password=app"]
