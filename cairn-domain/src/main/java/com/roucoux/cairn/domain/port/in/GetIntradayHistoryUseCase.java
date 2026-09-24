package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.IntradayPoint;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Inbound port: build the requested day's valuation curve. */
public interface GetIntradayHistoryUseCase {

    List<IntradayPoint> intraday(LocalDate date, ZoneId zone);
}
