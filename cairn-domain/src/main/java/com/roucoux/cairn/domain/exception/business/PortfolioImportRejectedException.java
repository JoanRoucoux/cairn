package com.roucoux.cairn.domain.exception.business;

import com.roucoux.cairn.domain.model.ImportError;
import java.util.List;

/**
 * Every invalid row of a refused import, not just the first: the caller is expected to fix the
 * whole file in one pass. Nothing was written when this is thrown.
 */
public class PortfolioImportRejectedException extends BusinessException {

    private final transient List<ImportError> errors;

    public PortfolioImportRejectedException(List<ImportError> errors) {
        super("Import rejected: " + errors.size() + " invalid row(s), nothing was written");
        this.errors = List.copyOf(errors);
    }

    public List<ImportError> errors() {
        return errors;
    }
}
