package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.SummaryPeriod;

public interface SendPeriodSummaryUseCase {

    void send(SummaryPeriod period);
}
