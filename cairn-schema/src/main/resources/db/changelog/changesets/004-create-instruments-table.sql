--liquibase formatted sql

--changeset cairn:004-create-instruments-table
CREATE TABLE instruments (
    id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    isin VARCHAR(12),
    currency CHAR(3) DEFAULT 'EUR' NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    price_source VARCHAR(20) NOT NULL,
    source_ref VARCHAR(64),
    description VARCHAR(280),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT instruments_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_instruments_isin
    ON instruments (isin) WHERE isin IS NOT NULL;

CREATE UNIQUE INDEX ux_instruments_source
    ON instruments (price_source, source_ref) WHERE source_ref IS NOT NULL;
