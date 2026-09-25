package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Snapshot;

public interface SaveSnapshotPort {

    void save(Snapshot snapshot);
}
