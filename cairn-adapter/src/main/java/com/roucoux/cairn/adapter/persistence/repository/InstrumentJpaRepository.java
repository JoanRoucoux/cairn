package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.InstrumentEntity;
import com.roucoux.cairn.domain.model.AssetClass;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentJpaRepository extends JpaRepository<InstrumentEntity, UUID> {

    @Query("select i from InstrumentEntity i where i.assetClass in :classes and i.priceSource <> 'MANUAL'")
    List<InstrumentEntity> findRefreshable(@Param("classes") Set<AssetClass> classes);
}
