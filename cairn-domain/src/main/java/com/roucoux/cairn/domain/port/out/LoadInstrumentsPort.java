package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Outbound port: read access to stored instruments. */
public interface LoadInstrumentsPort {

    List<Instrument> findAll();

    Optional<Instrument> findById(UUID id);

    List<Instrument> findRefreshable(Set<AssetClass> assetClasses);
}
