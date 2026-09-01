package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Reads the import CSV, the inverse of {@link HoldingCsvWriter} for the columns a caller can fill in. */
@Component
public class PortfolioCsvReader {

    public static final String HEADER = "account,accountType,institution,instrument,isinOrTicker,quantity,averageCost";

    private static final int COLUMNS = 7;
    /** Not a data row: the header or the file itself. */
    private static final int HEADER_ROW = -1;

    private static final char SEPARATOR = ',';
    private static final char QUOTE = '"';
    private static final String BYTE_ORDER_MARK = "﻿";

    /**
     * Structural reading only: shape, types and enums. Whether a row makes business sense is the
     * domain's call. Both refuse the same way, so a caller sees one list of reasons either way.
     */
    public List<ImportRow> read(String csv) {
        List<String> lines = lines(csv);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst().trim())) {
            throw new PortfolioImportRejectedException(
                    List.of(new ImportError(HEADER_ROW, "the first line must be exactly: " + HEADER)));
        }

        List<ImportRow> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        for (int line = 1; line < lines.size(); line++) {
            int rowIndex = line - 1;
            List<String> fields = splitFields(lines.get(line));
            if (fields.size() != COLUMNS) {
                errors.add(new ImportError(rowIndex, "expected " + COLUMNS + " columns, found " + fields.size()));
                continue;
            }
            try {
                rows.add(parse(fields));
            } catch (IllegalArgumentException invalid) {
                errors.add(new ImportError(rowIndex, invalid.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            throw new PortfolioImportRejectedException(errors);
        }
        return rows;
    }

    private static ImportRow parse(List<String> fields) {
        return new ImportRow(
                fields.get(0),
                accountType(fields.get(1)),
                fields.get(2),
                fields.get(3),
                fields.get(4),
                number("quantity", fields.get(5)),
                fields.get(6).isBlank() ? null : number("averageCost", fields.get(6)));
    }

    private static AccountType accountType(String value) {
        try {
            return AccountType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "unknown accountType '" + value + "': expected one of " + Arrays.toString(AccountType.values()));
        }
    }

    private static BigDecimal number(String column, String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(column + " '" + value + "' is not a number");
        }
    }

    private static List<String> lines(String csv) {
        String withoutBom = csv.startsWith(BYTE_ORDER_MARK) ? csv.substring(BYTE_ORDER_MARK.length()) : csv;
        return withoutBom.lines().filter(line -> !line.isBlank()).toList();
    }

    /** RFC 4180 quoting: the writer quotes any field holding a comma, so a plain split would tear it apart. */
    private static List<String> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted && c == QUOTE && i + 1 < line.length() && line.charAt(i + 1) == QUOTE) {
                field.append(QUOTE);
                i++;
            } else if (c == QUOTE) {
                quoted = !quoted;
            } else if (c == SEPARATOR && !quoted) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }
}
