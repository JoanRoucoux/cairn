--liquibase formatted sql

--changeset cairn:006-create-quotes-table
CREATE TABLE quotes (
    instrument_id UUID NOT NULL,
    as_of DATE NOT NULL,
    price NUMERIC(19, 6) NOT NULL,
    currency CHAR(3) NOT NULL,
    source VARCHAR(20) NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quotes_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE CASCADE
);

ALTER TABLE quotes ADD CONSTRAINT pk_quotes PRIMARY KEY (instrument_id, as_of);

CREATE INDEX ix_quotes_as_of ON quotes (as_of);
