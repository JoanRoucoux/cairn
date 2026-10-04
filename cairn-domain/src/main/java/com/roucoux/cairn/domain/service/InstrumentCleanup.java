package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.port.out.DeleteInstrumentPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import java.util.UUID;

final class InstrumentCleanup {

    private final LoadHoldingsPort loadHoldings;
    private final LoadInstrumentsPort loadInstruments;
    private final DeleteInstrumentPort deleteInstrument;

    InstrumentCleanup(
            LoadHoldingsPort loadHoldings, LoadInstrumentsPort loadInstruments, DeleteInstrumentPort deleteInstrument) {
        this.loadHoldings = loadHoldings;
        this.loadInstruments = loadInstruments;
        this.deleteInstrument = deleteInstrument;
    }

    void releaseIfUnheld(UUID instrumentId) {
        if (!loadHoldings.findByInstrument(instrumentId).isEmpty()) {
            return;
        }
        loadInstruments
                .findById(instrumentId)
                .filter(instrument -> instrument.assetClass() != AssetClass.CASH)
                .ifPresent(instrument -> deleteInstrument.delete(instrument.id()));
    }
}
