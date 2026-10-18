--liquibase formatted sql

--changeset cairn:020-drop-quotes-as-of-index
DROP INDEX IF EXISTS ix_quotes_as_of;
