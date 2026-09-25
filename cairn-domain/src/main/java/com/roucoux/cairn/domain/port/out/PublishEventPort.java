package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.event.DomainEvent;

public interface PublishEventPort {

    void publish(DomainEvent event);
}
