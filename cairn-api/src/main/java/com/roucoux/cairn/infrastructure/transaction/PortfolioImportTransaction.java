package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The import's all-or-nothing promise, which the domain cannot keep on its own: it is plain Java
 * with no Spring, so every port call would otherwise commit in its own transaction.
 *
 * <p>Deliberately not an implementation of {@link ImportPortfolioUseCase}: an inbound port is
 * implemented by domain services only, and ArchUnit enforces it. This is a caller that happens to
 * open a transaction first, which is also why the boundary is named rather than hidden in an
 * annotation on a controller method.
 */
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
