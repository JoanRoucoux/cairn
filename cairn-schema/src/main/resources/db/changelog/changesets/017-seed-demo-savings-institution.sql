--liquibase formatted sql

--changeset cairn:017-seed-demo-savings-institution context:demo
UPDATE accounts
   SET institution = 'Woodgrove Bank'
 WHERE id IN ('99999999-9999-9999-9999-999999999903', '99999999-9999-9999-9999-999999999904');
