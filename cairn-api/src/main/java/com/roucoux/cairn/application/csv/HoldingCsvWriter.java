package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.generated.model.HoldingResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Writes an RFC 4180 CSV export of the holdings, one row per holding. */
@Component
public class HoldingCsvWriter {

    private static final String HEADER =
            "account,instrument,isin,quantity,averageCost,price,marketValueEur,unrealizedGainEur,priceAsOf";
    private static final char SEPARATOR = ',';
    private static final String LINE_ENDING = "\r\n";
    private static final String BYTE_ORDER_MARK = "﻿";

    public String write(List<HoldingResponse> holdings) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(HEADER).append(LINE_ENDING);

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
                            .collect(Collectors.joining(String.valueOf(SEPARATOR))))
                    .append(LINE_ENDING);
        }

        return csv.toString();
    }

    private static String field(Object value) {
        if (value == null) {
            return "";
        }

        String text = value instanceof BigDecimal number ? number.toPlainString() : value.toString();
        boolean needsQuoting = text.chars().anyMatch(c -> c == '"' || c == SEPARATOR || c == '\r' || c == '\n');

        return needsQuoting ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
