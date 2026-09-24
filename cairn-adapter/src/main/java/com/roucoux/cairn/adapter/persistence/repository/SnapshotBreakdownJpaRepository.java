package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.SnapshotBreakdownEntity;
import com.roucoux.cairn.adapter.persistence.entity.SnapshotBreakdownId;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnapshotBreakdownJpaRepository extends JpaRepository<SnapshotBreakdownEntity, SnapshotBreakdownId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SnapshotBreakdownEntity b where b.id.asOf = :asOf")
    void deleteByAsOf(@Param("asOf") LocalDate asOf);
}
