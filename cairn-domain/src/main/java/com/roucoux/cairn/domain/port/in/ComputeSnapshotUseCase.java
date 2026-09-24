package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Snapshot;

/** Inbound port: compute and persist today's measured portfolio snapshot. */
public interface ComputeSnapshotUseCase {

    Snapshot compute();
}
