package com.roucoux.cairn.application.csv;

/**
 * What the export, the import template and the reader agree on. Without the separator hint, an
 * extension honoured by Excel and LibreOffice, Excel splits on the list separator of its own locale,
 * a semicolon in French, and the whole file lands in a single column.
 */
final class CsvFormat {

    static final String BYTE_ORDER_MARK = "﻿";
    static final String SEPARATOR_HINT_PREFIX = "sep=";
    static final String SEPARATOR_HINT = SEPARATOR_HINT_PREFIX + ",";
    static final String LINE_ENDING = "\r\n";

    /** Everything that comes before the header row. */
    static final String PREAMBLE = BYTE_ORDER_MARK + SEPARATOR_HINT + LINE_ENDING;

    private CsvFormat() {}
}
