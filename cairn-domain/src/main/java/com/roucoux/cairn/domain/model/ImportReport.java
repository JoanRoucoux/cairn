package com.roucoux.cairn.domain.model;

/** What an accepted import actually changed. */
public record ImportReport(int accountsCreated, int instrumentsCreated, int holdingsCreated, int holdingsUpdated) {}
