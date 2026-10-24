package com.roucoux.cairn.domain.model;

import java.time.Instant;

public record TelegramLink(LinkedChat chat, PendingLink pending) {

    public static final TelegramLink NONE = new TelegramLink(null, null);

    public TelegramLinkStatus status(Instant now) {
        if (pending != null && now.isBefore(pending.expiresAt())) {
            return TelegramLinkStatus.PENDING;
        }
        return chat != null ? TelegramLinkStatus.LINKED : TelegramLinkStatus.UNLINKED;
    }
}
