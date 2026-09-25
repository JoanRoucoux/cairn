package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Quote;
import java.util.List;

public interface SaveQuotePort {

    void upsert(Quote quote);

    void upsertAll(List<Quote> quotes);
}
