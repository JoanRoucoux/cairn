package com.roucoux.cairn.domain.port.out;

import java.util.UUID;

public interface DeleteAccountPort {

    void delete(UUID id);
}
