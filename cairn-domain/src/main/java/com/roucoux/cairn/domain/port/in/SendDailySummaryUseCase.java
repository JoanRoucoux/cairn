package com.roucoux.cairn.domain.port.in;

/** Inbound port: sends today's portfolio summary to Telegram. */
public interface SendDailySummaryUseCase {

    void send();
}
