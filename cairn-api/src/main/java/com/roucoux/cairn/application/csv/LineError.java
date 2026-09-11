package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.domain.model.ImportErrorCode;

/** A refused import row, located by its one-based line in the submitted file. */
public record LineError(int line, ImportErrorCode code, String value) {}
