package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.ImportRow;
import java.util.List;

public record ImportFile(List<ImportRow> rows, List<Integer> lines) {

    public ImportFile {
        rows = List.copyOf(rows);
        lines = List.copyOf(lines);
    }

    public ImportFileRejectedException locate(PortfolioImportRejectedException refused) {
        return new ImportFileRejectedException(refused.errors().stream()
                .map(error -> new LineError(lines.get(error.rowIndex()), error.code(), error.value()))
                .toList());
    }
}
