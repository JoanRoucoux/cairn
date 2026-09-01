package com.roucoux.cairn.domain.model;

/**
 * Why one submitted row was refused. Positions are indexes into the submitted list, not file
 * lines: turning an index back into a line number belongs to whoever parsed the file.
 */
public record ImportError(int rowIndex, String message) {}
