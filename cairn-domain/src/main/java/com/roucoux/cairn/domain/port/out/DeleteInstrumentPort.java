package com.roucoux.cairn.domain.port.out;

import java.util.UUID;

public interface DeleteInstrumentPort {

    void delete(UUID id);
}
