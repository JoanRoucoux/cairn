-- One-off, run once on a database that applied the XML changesets, BEFORE the first deploy of the
-- formatted SQL changelog. Liquibase identifies a changeset by id + author + filename: without this,
-- every changeset looks new and replays against the existing schema.
BEGIN;

UPDATE databasechangelog
   SET filename = regexp_replace(filename, '\.xml$', '.sql'),
       md5sum = NULL
 WHERE filename LIKE 'src/main/resources/db/changelog/changesets/%.xml'
   AND id IN (
       '002-spring-batch-metadata',
       '003-create-accounts-table',
       '004-create-instruments-table',
       '005-create-holdings-table',
       '006-create-quotes-table',
       '007-create-snapshots-tables',
       '008-create-quote-failures-table',
       '009-create-webauthn-tables',
       '010-drop-positions-table',
       '012-seed-demo',
       '013-create-spring-session-tables',
       '014-create-intraday-valuations-table',
       '015-add-instrument-symbol',
       '016-seed-demo-savings',
       '017-seed-demo-savings-institution');

COMMIT;
