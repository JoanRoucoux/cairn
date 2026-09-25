package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.ImportErrorCode;
import com.roucoux.cairn.domain.model.ImportRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class PortfolioCsvReader {

    private static final String SEPARATOR = String.valueOf(CsvFormat.SEPARATOR);

    public static final String HEADER = String.join(
            SEPARATOR,
            "account",
            "accountType",
            "institution",
            "instrument",
            "isinOrTicker",
            "quantity",
            "averageCost");

    private static final String COMMENT = "#";

    public static final String TEMPLATE = CsvFormat.BYTE_ORDER_MARK
            + String.join(
                    CsvFormat.LINE_ENDING,
                    HEADER,
                    example("Sample Broker", "PEA", "Sample Bank", "Sample S&P 500 ETF", "FR0011550185", "12", "26.65"),
                    example("Sample Broker", "CTO", "Sample Bank", "Sample Bank Share", "GLE.PA", "10", ""))
            + CsvFormat.LINE_ENDING;

    private static final int COLUMNS = 7;
    private static final int FIRST_LINE = 1;

    private static final char QUOTE = '"';

    public ImportFile read(String csv) {
        List<Line> lines = lines(csv);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst().text().trim())) {
            int line = lines.isEmpty() ? FIRST_LINE : lines.getFirst().number();
            throw new ImportFileRejectedException(List.of(new LineError(line, ImportErrorCode.BAD_HEADER, null)));
        }

        List<ImportRow> rows = new ArrayList<>();
        List<Integer> rowLines = new ArrayList<>();
        List<LineError> errors = new ArrayList<>();
        for (Line line : lines.subList(1, lines.size())) {
            List<String> fields = splitFields(line.text());
            if (fields.size() != COLUMNS) {
                errors.add(new LineError(
                        line.number(), ImportErrorCode.WRONG_COLUMN_COUNT, String.valueOf(fields.size())));
                continue;
            }
            try {
                rows.add(parse(fields));
                rowLines.add(line.number());
            } catch (UnreadableFieldException invalid) {
                errors.add(new LineError(line.number(), invalid.code, invalid.value));
            }
        }

        if (!errors.isEmpty()) {
            throw new ImportFileRejectedException(errors);
        }
        return new ImportFile(rows, rowLines);
    }

    private static String example(String... fields) {
        return COMMENT + " " + String.join(SEPARATOR, fields);
    }

    private static ImportRow parse(List<String> fields) {
        return new ImportRow(
                fields.get(0),
                accountType(fields.get(1)),
                fields.get(2),
                fields.get(3),
                fields.get(4),
                number(fields.get(5)),
                fields.get(6).isBlank() ? null : number(fields.get(6)));
    }

    private static AccountType accountType(String value) {
        try {
            return AccountType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new UnreadableFieldException(ImportErrorCode.UNKNOWN_ACCOUNT_TYPE, value);
        }
    }

    private static BigDecimal number(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException notANumber) {
            throw new UnreadableFieldException(ImportErrorCode.NOT_A_NUMBER, value);
        }
    }

    private static final class UnreadableFieldException extends RuntimeException {

        private final transient ImportErrorCode code;
        private final transient String value;

        private UnreadableFieldException(ImportErrorCode code, String value) {
            super(null, null, false, false);
            this.code = code;
            this.value = value;
        }
    }

    private record Line(int number, String text) {}

    private static List<Line> lines(String csv) {
        String withoutBom =
                csv.startsWith(CsvFormat.BYTE_ORDER_MARK) ? csv.substring(CsvFormat.BYTE_ORDER_MARK.length()) : csv;
        List<String> texts = withoutBom.lines().toList();

        return IntStream.range(0, texts.size())
                .mapToObj(index -> new Line(index + FIRST_LINE, texts.get(index)))
                .filter(line -> !line.text().isBlank() && !line.text().strip().startsWith(COMMENT))
                .toList();
    }

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
            } else if (c == CsvFormat.SEPARATOR && !quoted) {
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
