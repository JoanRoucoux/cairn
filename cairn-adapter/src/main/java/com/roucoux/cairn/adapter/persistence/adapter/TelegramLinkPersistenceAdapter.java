package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.TelegramLinkEntity;
import com.roucoux.cairn.adapter.persistence.repository.TelegramLinkJpaRepository;
import com.roucoux.cairn.domain.model.TelegramLink;
import com.roucoux.cairn.domain.port.out.TelegramLinkPort;
import org.springframework.stereotype.Component;

@Component
public class TelegramLinkPersistenceAdapter implements TelegramLinkPort {

    private final TelegramLinkJpaRepository repository;

    public TelegramLinkPersistenceAdapter(TelegramLinkJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TelegramLink load() {
        return repository
                .findById(TelegramLinkEntity.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("telegram link row is missing"))
                .toDomain();
    }

    @Override
    public TelegramLink save(TelegramLink link) {
        return repository.save(TelegramLinkEntity.fromDomain(link)).toDomain();
    }
}
