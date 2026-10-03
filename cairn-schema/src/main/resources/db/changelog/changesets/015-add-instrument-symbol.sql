--liquibase formatted sql

--changeset cairn:015-add-instrument-symbol
ALTER TABLE instruments ADD symbol VARCHAR(32);

UPDATE instruments SET symbol = source_ref
 WHERE price_source = 'YAHOO' AND source_ref IS NOT NULL AND length(source_ref) <= 32;
