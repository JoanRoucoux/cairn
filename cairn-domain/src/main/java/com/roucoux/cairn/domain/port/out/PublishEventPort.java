package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.event.DomainEvent;

/** Outbound port: hands a domain event to whatever messaging system the adapter wires it to. */
public interface PublishEventPort {

    void publish(DomainEvent event);
}
