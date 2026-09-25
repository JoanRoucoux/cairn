package com.roucoux.cairn.domain.model;

public record ImportError(int rowIndex, ImportErrorCode code, String value) {}
