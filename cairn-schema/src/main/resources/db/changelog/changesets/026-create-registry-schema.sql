--liquibase formatted sql

--changeset cairn:026-create-registry-schema
CREATE SCHEMA IF NOT EXISTS registry;
