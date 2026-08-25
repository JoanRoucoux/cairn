package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Quote;

/** Inbound port: read the current price of an instrument. */
public interface GetQuoteUseCase {

    Quote byIsin(String isin);
}
