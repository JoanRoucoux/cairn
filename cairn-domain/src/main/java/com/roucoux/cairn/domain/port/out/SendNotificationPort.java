package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.DailySummary;

/** Outbound port: delivers a daily summary. Raises {@code TechnicalException} on failure. */
public interface SendNotificationPort {

    void send(DailySummary summary);
}
