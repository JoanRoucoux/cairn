--liquibase formatted sql

--changeset cairn:010-drop-positions-table
--preconditions onFail:MARK_RAN
--precondition-table-exists table:positions
DROP TABLE positions;
