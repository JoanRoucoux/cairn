package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.SnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotJpaRepository extends JpaRepository<SnapshotEntity, LocalDate> {

    List<SnapshotEntity> findByAsOfBetweenOrderByAsOf(LocalDate from, LocalDate to);
}
