package com.roucoux.cairn.domain.model;

import java.time.Instant;

public record LinkedChat(long chatId, String label, Instant linkedAt) {}
