package com.roucoux.cairn.application.csv;

/**
 * What the export, the import template and the reader agree on. The semicolon is what Excel splits
 * on in a French locale, and the byte order mark is what makes it read the accents.
 */
final class CsvFormat {

    static final String BYTE_ORDER_MARK = "﻿";
    static final char SEPARATOR = ';';
    static final String LINE_ENDING = "\r\n";

    private CsvFormat() {}
}
