package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Snapshot;
import java.time.LocalDate;
import java.util.List;

public interface LoadSnapshotsPort {

    List<Snapshot> findBetween(LocalDate from, LocalDate to);
}
