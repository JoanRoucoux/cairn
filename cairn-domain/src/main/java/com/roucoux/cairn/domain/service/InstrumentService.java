package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ManageInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.DeleteInstrumentPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.util.UUID;

public class InstrumentService implements ManageInstrumentUseCase {

    private final LoadInstrumentsPort loadInstruments;
    private final SaveInstrumentPort saveInstrument;
    private final DeleteInstrumentPort deleteInstrument;
    private final LoadHoldingsPort loadHoldings;
    private final DeleteHoldingPort deleteHolding;

    public InstrumentService(
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            DeleteInstrumentPort deleteInstrument,
            LoadHoldingsPort loadHoldings,
            DeleteHoldingPort deleteHolding) {
        this.loadInstruments = loadInstruments;
        this.saveInstrument = saveInstrument;
        this.deleteInstrument = deleteInstrument;
        this.loadHoldings = loadHoldings;
        this.deleteHolding = deleteHolding;
    }

    @Override
    public Instrument create(
            String name,
            String isin,
            String currency,
            AssetClass assetClass,
            PriceSource priceSource,
            String sourceRef,
            String description) {
        return saveInstrument.save(new Instrument(
                UUID.randomUUID(), name, isin, currency, assetClass, priceSource, sourceRef, description));
    }

    @Override
    public Instrument update(
            UUID id,
            String name,
            String isin,
            AssetClass assetClass,
            PriceSource priceSource,
            String sourceRef,
            String description) {
        Instrument existing = loadInstruments.findById(id).orElseThrow(() -> new NotFoundException("instrument", id));
        return saveInstrument.save(new Instrument(
                existing.id(), name, isin, existing.currency(), assetClass, priceSource, sourceRef, description));
    }

    @Override
    public void delete(UUID id) {
        loadInstruments.findById(id).orElseThrow(() -> new NotFoundException("instrument", id));
        loadHoldings.findByInstrument(id).forEach(holding -> deleteHolding.delete(holding.id()));
        deleteInstrument.delete(id);
    }
}
