package com.roucoux.cairn.domain.model;

/**
 * Why one submitted row was refused: a code, and the offending token when there is one.
 *
 * <p>Positions are indexes into the submitted list, not file lines: turning an index back into a
 * line number belongs to whoever parsed the file. {@code rowIndex} is negative when the failure is
 * about the file itself rather than a row.
 */
public record ImportError(int rowIndex, ImportErrorCode code, String value) {}
