--liquibase formatted sql

--changeset cairn:019-delete-unheld-instruments
DELETE FROM instruments i
WHERE i.asset_class <> 'CASH'
  AND NOT EXISTS (SELECT 1 FROM holdings h WHERE h.instrument_id = i.id);
