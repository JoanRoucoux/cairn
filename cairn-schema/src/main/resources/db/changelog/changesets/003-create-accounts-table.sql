--liquibase formatted sql

--changeset cairn:003-create-accounts-table
CREATE TABLE accounts (
    id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    type VARCHAR(20) NOT NULL,
    institution VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (id),
    UNIQUE (name)
);
