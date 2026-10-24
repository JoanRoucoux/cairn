package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TelegramLinkTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final LinkedChat CHAT = new LinkedChat(42L, "Alex", NOW.minusSeconds(3600));

    @Test
    void noneIsUnlinked() {
        assertThat(TelegramLink.NONE.status(NOW)).isEqualTo(TelegramLinkStatus.UNLINKED);
    }

    @Test
    void aChatWithoutAPendingTokenIsLinked() {
        assertThat(new TelegramLink(CHAT, null).status(NOW)).isEqualTo(TelegramLinkStatus.LINKED);
    }

    @Test
    void aFreshPendingTokenWinsOverTheLinkedChat() {
        TelegramLink link = new TelegramLink(CHAT, new PendingLink("hash", NOW.plusSeconds(60)));

        assertThat(link.status(NOW)).isEqualTo(TelegramLinkStatus.PENDING);
    }

    @Test
    void aFreshPendingTokenWithoutAChatIsPending() {
        TelegramLink link = new TelegramLink(null, new PendingLink("hash", NOW.plusSeconds(60)));

        assertThat(link.status(NOW)).isEqualTo(TelegramLinkStatus.PENDING);
    }

    @Test
    void anExpiredPendingTokenWithAChatAnswersLinked() {
        TelegramLink link = new TelegramLink(CHAT, new PendingLink("hash", NOW));

        assertThat(link.status(NOW)).isEqualTo(TelegramLinkStatus.LINKED);
    }

    @Test
    void anExpiredPendingTokenWithoutAChatAnswersUnlinked() {
        TelegramLink link = new TelegramLink(null, new PendingLink("hash", NOW.minusSeconds(1)));

        assertThat(link.status(NOW)).isEqualTo(TelegramLinkStatus.UNLINKED);
    }
}
