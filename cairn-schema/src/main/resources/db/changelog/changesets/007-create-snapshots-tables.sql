--liquibase formatted sql

--changeset cairn:007-create-snapshots-tables
CREATE TABLE snapshots (
    as_of DATE NOT NULL,
    total_eur NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT snapshots_pkey PRIMARY KEY (as_of)
);

CREATE TABLE snapshot_breakdowns (
    as_of DATE NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    breakdown_key VARCHAR(80) NOT NULL,
    value_eur NUMERIC(19, 4) NOT NULL,
    CONSTRAINT fk_breakdowns_snapshot FOREIGN KEY (as_of) REFERENCES snapshots (as_of) ON DELETE CASCADE
);

ALTER TABLE snapshot_breakdowns ADD CONSTRAINT pk_snapshot_breakdowns PRIMARY KEY (as_of, dimension, breakdown_key);
