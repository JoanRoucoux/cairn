--liquibase formatted sql

--changeset cairn:025-create-telegram-link-table
CREATE TABLE telegram_link (
    id SMALLINT NOT NULL DEFAULT 1,
    chat_id BIGINT,
    chat_label VARCHAR(100),
    linked_at TIMESTAMP WITH TIME ZONE,
    pending_token_hash CHAR(64),
    pending_expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT telegram_link_pkey PRIMARY KEY (id),
    CONSTRAINT telegram_link_single_row CHECK (id = 1),
    CONSTRAINT telegram_link_chat_check CHECK ((chat_id IS NULL) = (linked_at IS NULL)),
    CONSTRAINT telegram_link_pending_check CHECK ((pending_token_hash IS NULL) = (pending_expires_at IS NULL))
);

INSERT INTO telegram_link (id) VALUES (1);
