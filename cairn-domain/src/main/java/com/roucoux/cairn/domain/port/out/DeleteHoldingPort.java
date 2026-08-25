package com.roucoux.cairn.domain.port.out;

import java.util.UUID;

/** Outbound port: remove a holding. */
public interface DeleteHoldingPort {

    void delete(UUID id);
}
