package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.PreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferencesJpaRepository extends JpaRepository<PreferencesEntity, Short> {}
