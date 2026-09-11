package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.ImportRow;
import java.util.List;

/**
 * The rows read from an import file, each with the line it came from. The line of a row is not its
 * index plus a constant: blank and commented lines in between are skipped.
 */
public record ImportFile(List<ImportRow> rows, List<Integer> lines) {

    public ImportFile {
        rows = List.copyOf(rows);
        lines = List.copyOf(lines);
    }

    /** Places the domain's refusals, which count rows from zero, back on the lines of this file. */
    public ImportFileRejectedException locate(PortfolioImportRejectedException refused) {
        return new ImportFileRejectedException(refused.errors().stream()
                .map(error -> new LineError(lines.get(error.rowIndex()), error.code(), error.value()))
                .toList());
    }
}
