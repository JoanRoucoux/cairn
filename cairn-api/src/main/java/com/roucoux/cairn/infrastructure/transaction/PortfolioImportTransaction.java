package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.model.ImportPlan;
import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PortfolioImportTransaction {

    private final ImportPortfolioUseCase importPortfolio;
    private final TransactionTemplate transaction;

    PortfolioImportTransaction(ImportPortfolioUseCase importPortfolio, PlatformTransactionManager transactionManager) {
        this.importPortfolio = importPortfolio;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ImportReport run(List<ImportRow> rows) {
        ImportPlan plan = importPortfolio.prepare(rows);
        return transaction.execute(status -> importPortfolio.apply(plan));
    }
}
