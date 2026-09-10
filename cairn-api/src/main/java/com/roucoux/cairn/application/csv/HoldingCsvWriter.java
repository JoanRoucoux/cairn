package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.generated.model.HoldingResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Writes the CSV export of the holdings, one row per holding. */
@Component
public class HoldingCsvWriter {

    private static final String HEADER = String.join(
            String.valueOf(CsvFormat.SEPARATOR),
            "account",
            "instrument",
            "isin",
            "quantity",
            "averageCost",
            "price",
            "marketValueEur",
            "unrealizedGainEur",
            "priceAsOf");

    public String write(List<HoldingResponse> holdings) {
        StringBuilder csv =
                new StringBuilder(CsvFormat.BYTE_ORDER_MARK).append(HEADER).append(CsvFormat.LINE_ENDING);

        for (HoldingResponse holding : holdings) {
            csv.append(Stream.of(
                                    holding.getAccountName(),
                                    holding.getInstrumentName(),
                                    holding.getIsin(),
                                    holding.getQuantity(),
                                    holding.getAverageCost(),
                                    holding.getPrice(),
                                    holding.getMarketValueEur(),
                                    holding.getUnrealizedGainEur(),
                                    holding.getPriceAsOf())
                            .map(HoldingCsvWriter::field)
                            .collect(Collectors.joining(String.valueOf(CsvFormat.SEPARATOR))))
                    .append(CsvFormat.LINE_ENDING);
        }

        return csv.toString();
    }

    private static String field(Object value) {
        if (value == null) {
            return "";
        }

        String text = value instanceof BigDecimal number ? number.toPlainString() : value.toString();
        boolean needsQuoting =
                text.chars().anyMatch(c -> c == '"' || c == CsvFormat.SEPARATOR || c == '\r' || c == '\n');

        return needsQuoting ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
