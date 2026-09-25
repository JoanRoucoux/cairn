package com.roucoux.cairn.adapter.messaging.adapter;

import java.time.Instant;

record EventEnvelope(String id, String type, int version, Instant occurredAt, String source, Object data) {}
