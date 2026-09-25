package com.roucoux.cairn.domain.port.out;

import java.util.UUID;

public interface DeleteHoldingPort {

    void delete(UUID id);
}
