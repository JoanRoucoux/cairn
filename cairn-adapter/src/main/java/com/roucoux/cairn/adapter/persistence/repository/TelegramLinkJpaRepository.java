package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.TelegramLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramLinkJpaRepository extends JpaRepository<TelegramLinkEntity, Short> {}
