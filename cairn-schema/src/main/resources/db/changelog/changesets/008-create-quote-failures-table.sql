--liquibase formatted sql

--changeset cairn:008-create-quote-failures-table
CREATE TABLE quote_failures (
    id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    CONSTRAINT quote_failures_pkey PRIMARY KEY (id),
    CONSTRAINT fk_failures_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE CASCADE
);

CREATE INDEX ix_quote_failures_instrument ON quote_failures (instrument_id, occurred_at DESC);
