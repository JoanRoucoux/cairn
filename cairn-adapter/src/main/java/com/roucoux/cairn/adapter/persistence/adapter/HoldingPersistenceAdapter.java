package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.HoldingEntity;
import com.roucoux.cairn.adapter.persistence.repository.HoldingJpaRepository;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's read, write and delete ports for holdings with Spring Data JPA. */
@Component
public class HoldingPersistenceAdapter implements LoadHoldingsPort, SaveHoldingPort, DeleteHoldingPort {

    private final HoldingJpaRepository repository;

    public HoldingPersistenceAdapter(HoldingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Holding> findAll() {
        return repository.findAll().stream().map(HoldingEntity::toDomain).toList();
    }

    @Override
    public Optional<Holding> findById(UUID id) {
        return repository.findById(id).map(HoldingEntity::toDomain);
    }

    @Override
    public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
        return repository
                .findByAccountIdAndInstrumentId(accountId, instrumentId)
                .map(HoldingEntity::toDomain);
    }

    @Override
    public Holding save(Holding holding) {
        return repository.save(HoldingEntity.fromDomain(holding)).toDomain();
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
