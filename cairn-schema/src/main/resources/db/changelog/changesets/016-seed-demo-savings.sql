--liquibase formatted sql

--changeset cairn:016-seed-demo-savings context:demo
INSERT INTO instruments (id, name, currency, asset_class, price_source, source_ref, created_at)
SELECT '99999999-9999-9999-9999-999999999915', 'Euros', 'EUR', 'CASH', 'MANUAL', 'EUR', now()
 WHERE NOT EXISTS (
    SELECT 1 FROM instruments WHERE price_source = 'MANUAL' AND source_ref = 'EUR');

UPDATE accounts SET institution = 'Fortuneo', name = 'Livret A' WHERE id = '99999999-9999-9999-9999-999999999903';

INSERT INTO accounts (id, name, type, institution, created_at) VALUES ('99999999-9999-9999-9999-999999999904', 'LDDS', 'SAVINGS', 'Fortuneo', now());

UPDATE holdings
   SET instrument_id = (SELECT id FROM instruments WHERE price_source = 'MANUAL' AND source_ref = 'EUR')
 WHERE id = '99999999-9999-9999-9999-999999999924'
   AND NOT EXISTS (
       SELECT 1 FROM holdings
        WHERE account_id = '99999999-9999-9999-9999-999999999903'
          AND instrument_id = (SELECT id FROM instruments WHERE price_source = 'MANUAL' AND source_ref = 'EUR'));

DELETE FROM holdings
 WHERE id = '99999999-9999-9999-9999-999999999924'
   AND instrument_id = '99999999-9999-9999-9999-999999999914';

INSERT INTO holdings (id, account_id, instrument_id, quantity, average_cost, updated_at)
SELECT '99999999-9999-9999-9999-999999999925', '99999999-9999-9999-9999-999999999904', id, 500, 1, now()
  FROM instruments WHERE price_source = 'MANUAL' AND source_ref = 'EUR';

DELETE FROM quotes WHERE instrument_id = '99999999-9999-9999-9999-999999999914';

DELETE FROM instruments WHERE id = '99999999-9999-9999-9999-999999999914';
