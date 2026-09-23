package com.roucoux.cairn.adapter.messaging.adapter;

import java.time.Instant;

/** Wire format every event is published as, regardless of which {@code DomainEvent} it carries. */
record EventEnvelope(String id, String type, int version, Instant occurredAt, String source, Object data) {}
