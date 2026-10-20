--liquibase formatted sql

--changeset cairn:023-widen-prices
ALTER TABLE quotes ALTER COLUMN price TYPE NUMERIC(28, 12);
ALTER TABLE holdings ALTER COLUMN average_cost TYPE NUMERIC(28, 12);
