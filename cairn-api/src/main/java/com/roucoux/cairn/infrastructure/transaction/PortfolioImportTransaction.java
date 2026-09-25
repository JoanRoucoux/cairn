package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PortfolioImportTransaction {

    private final ImportPortfolioUseCase importPortfolio;

    PortfolioImportTransaction(ImportPortfolioUseCase importPortfolio) {
        this.importPortfolio = importPortfolio;
    }

    @Transactional
    public ImportReport run(List<ImportRow> rows) {
        return importPortfolio.importPortfolio(rows);
    }
}
