package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Snapshot;

/** Outbound port: upsert today's measured snapshot, replacing any earlier run's ventilations. */
public interface SaveSnapshotPort {

    void save(Snapshot snapshot);
}
