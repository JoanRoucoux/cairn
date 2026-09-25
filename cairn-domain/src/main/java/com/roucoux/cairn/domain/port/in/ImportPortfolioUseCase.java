package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import java.util.List;

public interface ImportPortfolioUseCase {

    ImportReport importPortfolio(List<ImportRow> rows);
}
