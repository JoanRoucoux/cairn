--liquibase formatted sql

--changeset cairn:012-seed-demo context:demo
INSERT INTO accounts (id, name, type, institution, created_at) VALUES ('99999999-9999-9999-9999-999999999901', 'Demo Broker', 'PEA', 'Demo Bank', now());
INSERT INTO accounts (id, name, type, institution, created_at) VALUES ('99999999-9999-9999-9999-999999999902', 'Demo Exchange', 'CRYPTO', 'Demo Exchange', now());
INSERT INTO accounts (id, name, type, institution, created_at) VALUES ('99999999-9999-9999-9999-999999999903', 'Demo Savings', 'SAVINGS', 'Demo Bank', now());

INSERT INTO instruments (id, name, isin, currency, asset_class, price_source, source_ref, created_at) VALUES ('99999999-9999-9999-9999-999999999911', 'Global Growth Tracker', 'LU9999999991', 'EUR', 'ETF', 'YAHOO', 'GGT.PA', now());
INSERT INTO instruments (id, name, isin, currency, asset_class, price_source, source_ref, created_at) VALUES ('99999999-9999-9999-9999-999999999912', 'Northwind Traders', 'FR9999999992', 'EUR', 'EQUITY', 'YAHOO', 'NWT.PA', now());
INSERT INTO instruments (id, name, currency, asset_class, price_source, source_ref, created_at) VALUES ('99999999-9999-9999-9999-999999999913', 'Bitcoin', 'EUR', 'CRYPTO', 'COINGECKO', 'bitcoin', now());
INSERT INTO instruments (id, name, currency, asset_class, price_source, created_at) VALUES ('99999999-9999-9999-9999-999999999914', 'Livret A', 'EUR', 'CASH', 'MANUAL', now());

INSERT INTO holdings (id, account_id, instrument_id, quantity, average_cost, updated_at) VALUES ('99999999-9999-9999-9999-999999999921', '99999999-9999-9999-9999-999999999901', '99999999-9999-9999-9999-999999999911', 100, 20.00, now());
INSERT INTO holdings (id, account_id, instrument_id, quantity, average_cost, updated_at) VALUES ('99999999-9999-9999-9999-999999999922', '99999999-9999-9999-9999-999999999901', '99999999-9999-9999-9999-999999999912', 10, 50.00, now());
INSERT INTO holdings (id, account_id, instrument_id, quantity, updated_at) VALUES ('99999999-9999-9999-9999-999999999923', '99999999-9999-9999-9999-999999999902', '99999999-9999-9999-9999-999999999913', 0.1, now());
INSERT INTO holdings (id, account_id, instrument_id, quantity, average_cost, updated_at) VALUES ('99999999-9999-9999-9999-999999999924', '99999999-9999-9999-9999-999999999903', '99999999-9999-9999-9999-999999999914', 1000, 1, now());
