package com.roucoux.cairn.application.csv;

import java.util.List;

/** Every reason an import file was refused, each on the line a person reading the file would look at. */
public class ImportFileRejectedException extends RuntimeException {

    private final transient List<LineError> errors;

    public ImportFileRejectedException(List<LineError> errors) {
        super(errors.size() + " line(s) refused");
        this.errors = List.copyOf(errors);
    }

    public List<LineError> errors() {
        return errors;
    }
}
