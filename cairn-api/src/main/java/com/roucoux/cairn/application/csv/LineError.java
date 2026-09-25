package com.roucoux.cairn.application.csv;

import com.roucoux.cairn.domain.model.ImportErrorCode;

public record LineError(int line, ImportErrorCode code, String value) {}
