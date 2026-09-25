package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import java.time.LocalDate;
import java.util.List;

public interface GetHistoryUseCase {

    List<HistoryPoint> history(HistoryMode mode, LocalDate from, LocalDate to);
}
