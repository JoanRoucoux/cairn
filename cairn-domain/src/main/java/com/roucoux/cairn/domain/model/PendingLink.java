package com.roucoux.cairn.domain.model;

import java.time.Instant;

public record PendingLink(String tokenHash, Instant expiresAt) {}
