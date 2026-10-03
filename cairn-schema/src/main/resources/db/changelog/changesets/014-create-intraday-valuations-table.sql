--liquibase formatted sql

--changeset cairn:014-create-intraday-valuations-table
CREATE TABLE intraday_valuations (
    at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_eur NUMERIC(19, 4) NOT NULL,
    CONSTRAINT pk_intraday_valuations PRIMARY KEY (at)
);
