package com.roucoux.cairn.domain.model;

import java.time.Instant;

public record LinkInvitation(String botUsername, String token, Instant expiresAt) {}
