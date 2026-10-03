--liquibase formatted sql

--changeset cairn:009-create-webauthn-tables
CREATE TABLE user_entities (
    id VARCHAR(1000) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    CONSTRAINT user_entities_pkey PRIMARY KEY (id)
);

CREATE TABLE user_credentials (
    credential_id VARCHAR(1000) NOT NULL,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    public_key BYTEA NOT NULL,
    signature_count BIGINT,
    uv_initialized BOOLEAN,
    backup_eligible BOOLEAN NOT NULL,
    authenticator_transports VARCHAR(1000),
    public_key_credential_type VARCHAR(100),
    backup_state BOOLEAN NOT NULL,
    attestation_object BYTEA,
    attestation_client_data_json BYTEA,
    created TIMESTAMP WITHOUT TIME ZONE,
    last_used TIMESTAMP WITHOUT TIME ZONE,
    label VARCHAR(1000) NOT NULL,
    CONSTRAINT user_credentials_pkey PRIMARY KEY (credential_id)
);
