package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.ImportPlan;
import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import java.util.List;

public interface ImportPortfolioUseCase {

    ImportPlan prepare(List<ImportRow> rows);

    ImportReport apply(ImportPlan plan);
}
