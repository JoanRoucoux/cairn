package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import java.time.LocalDate;
import java.util.List;

public interface FetchQuotePort {

    boolean supports(PriceSource source);

    Quote fetch(Instrument instrument);

    List<Quote> fetchHistory(Instrument instrument, LocalDate from);
}
