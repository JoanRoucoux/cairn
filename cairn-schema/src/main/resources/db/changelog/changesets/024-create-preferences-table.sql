--liquibase formatted sql

--changeset cairn:024-create-preferences-table
CREATE TABLE preferences (
    id SMALLINT NOT NULL DEFAULT 1,
    language VARCHAR(2) NOT NULL DEFAULT 'fr',
    theme VARCHAR(6) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT preferences_pkey PRIMARY KEY (id),
    CONSTRAINT preferences_single_row CHECK (id = 1),
    CONSTRAINT preferences_language_check CHECK (language IN ('fr', 'en')),
    CONSTRAINT preferences_theme_check CHECK (theme IN ('system', 'light', 'dark'))
);

INSERT INTO preferences (id) VALUES (1);
