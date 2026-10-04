--liquibase formatted sql

--changeset cairn:018-drop-instrument-isin-uniqueness
DROP INDEX IF EXISTS ux_instruments_isin;

CREATE INDEX ix_instruments_isin
    ON instruments (isin) WHERE isin IS NOT NULL;
