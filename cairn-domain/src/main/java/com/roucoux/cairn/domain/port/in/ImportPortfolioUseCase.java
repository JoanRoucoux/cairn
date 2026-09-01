package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import java.util.List;

/** Inbound port: load a whole portfolio at once, creating whatever the rows refer to and does not exist yet. */
public interface ImportPortfolioUseCase {

    /**
     * All or nothing: either every row is valid and applied, or nothing is written and a
     * {@link com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException} carries
     * every reason.
     */
    ImportReport importPortfolio(List<ImportRow> rows);
}
