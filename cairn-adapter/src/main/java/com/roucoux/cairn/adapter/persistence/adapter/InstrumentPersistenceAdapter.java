package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.InstrumentEntity;
import com.roucoux.cairn.adapter.persistence.repository.InstrumentJpaRepository;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's read and write ports for instruments with Spring Data JPA. */
@Component
public class InstrumentPersistenceAdapter implements LoadInstrumentsPort, SaveInstrumentPort {

    private final InstrumentJpaRepository repository;

    public InstrumentPersistenceAdapter(InstrumentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Instrument> findAll() {
        return repository.findAll().stream().map(InstrumentEntity::toDomain).toList();
    }

    @Override
    public Optional<Instrument> findById(UUID id) {
        return repository.findById(id).map(InstrumentEntity::toDomain);
    }

    @Override
    public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
        return repository.findRefreshable(assetClasses).stream()
                .map(InstrumentEntity::toDomain)
                .toList();
    }

    @Override
    public Instrument save(Instrument instrument) {
        return repository.save(InstrumentEntity.fromDomain(instrument)).toDomain();
    }
}
