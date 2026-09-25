package com.roucoux.cairn.application.csv;

/** Semicolon and byte order mark: what Excel needs in a French locale to split columns and read accents. */
final class CsvFormat {

    static final String BYTE_ORDER_MARK = "﻿";
    static final char SEPARATOR = ';';
    static final String LINE_ENDING = "\r\n";

    private CsvFormat() {}
}
