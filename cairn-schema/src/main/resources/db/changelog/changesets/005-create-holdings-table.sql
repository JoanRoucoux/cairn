--liquibase formatted sql

--changeset cairn:005-create-holdings-table
CREATE TABLE holdings (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    quantity NUMERIC(28, 12) NOT NULL,
    average_cost NUMERIC(19, 6),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT holdings_pkey PRIMARY KEY (id),
    CONSTRAINT fk_holdings_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_holdings_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id)
);

ALTER TABLE holdings ADD CONSTRAINT ux_holdings_account_instrument UNIQUE (account_id, instrument_id);
