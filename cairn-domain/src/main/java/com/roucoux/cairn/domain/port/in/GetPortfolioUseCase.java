package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Portfolio;

/** Inbound port: build the current, fully valued portfolio. */
public interface GetPortfolioUseCase {

    Portfolio get();
}
