--liquibase formatted sql

--changeset cairn:021-index-holdings-instrument
CREATE INDEX ix_holdings_instrument ON holdings (instrument_id);
