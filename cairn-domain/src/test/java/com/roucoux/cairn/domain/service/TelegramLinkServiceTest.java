package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.TelegramNotConfiguredException;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.LinkInvitation;
import com.roucoux.cairn.domain.model.LinkedChat;
import com.roucoux.cairn.domain.model.PendingLink;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.StartCommand;
import com.roucoux.cairn.domain.model.TelegramLink;
import com.roucoux.cairn.domain.model.TelegramLinkStatus;
import com.roucoux.cairn.domain.model.TelegramLinkView;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.domain.port.out.TelegramBotPort;
import com.roucoux.cairn.domain.port.out.TelegramLinkPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @Test
    void statusIsUnavailableWhenTheBotIsNotConfigured() {
        Fixture fixture = new Fixture();
        fixture.configured = false;

        assertThat(fixture.service().status().status()).isEqualTo(TelegramLinkStatus.UNAVAILABLE);
    }

    @Test
    void statusReflectsTheStoredLink() {
        Fixture fixture = new Fixture();
        LinkedChat chat = new LinkedChat(7L, "Alex", NOW.minusSeconds(60));
        fixture.link = new TelegramLink(chat, null);

        assertThat(fixture.service().status()).isEqualTo(new TelegramLinkView(TelegramLinkStatus.LINKED, chat));
    }

    @Test
    void startRefusesWhenTheBotIsNotConfigured() {
        Fixture fixture = new Fixture();
        fixture.configured = false;

        assertThatThrownBy(() -> fixture.service().start())
                .isInstanceOf(TelegramNotConfiguredException.class)
                .hasMessage("Telegram bot is not configured");
    }

    @Test
    void checkRefusesWhenTheBotIsNotConfigured() {
        Fixture fixture = new Fixture();
        fixture.configured = false;

        assertThatThrownBy(() -> fixture.service().check()).isInstanceOf(TelegramNotConfiguredException.class);
    }

    @Test
    void startStoresOnlyTheHashOfA43CharacterUrlSafeToken() {
        Fixture fixture = new Fixture();

        LinkInvitation invitation = fixture.service().start();

        assertThat(invitation.token()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(invitation.botUsername()).isEqualTo("cairn_bot");
        assertThat(invitation.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(fixture.link.pending())
                .isEqualTo(new PendingLink(sha256(invitation.token()), NOW.plus(Duration.ofMinutes(10))));
        assertThat(fixture.link.pending().tokenHash()).doesNotContain(invitation.token());
    }

    @Test
    void startKeepsTheLinkedChat() {
        Fixture fixture = new Fixture();
        LinkedChat chat = new LinkedChat(7L, "Alex", NOW.minusSeconds(60));
        fixture.link = new TelegramLink(chat, null);

        fixture.service().start();

        assertThat(fixture.link.chat()).isEqualTo(chat);
        assertThat(fixture.link.pending()).isNotNull();
    }

    @Test
    void checkLinksTheChatWhoseStartPayloadMatches() {
        Fixture fixture = new Fixture();
        LinkInvitation invitation = fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "Alex", invitation.token()));

        TelegramLinkView view = fixture.service().check();

        LinkedChat expected = new LinkedChat(99L, "Alex", NOW);
        assertThat(view).isEqualTo(new TelegramLinkView(TelegramLinkStatus.LINKED, expected));
        assertThat(fixture.link).isEqualTo(new TelegramLink(expected, null));
        assertThat(fixture.confirmations).containsExactly(new Confirmation(99L, Language.FR));
    }

    @Test
    void checkConfirmsInTheStoredLanguage() {
        Fixture fixture = new Fixture();
        fixture.language = Language.EN;
        LinkInvitation invitation = fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "Alex", invitation.token()));

        fixture.service().check();

        assertThat(fixture.confirmations).containsExactly(new Confirmation(99L, Language.EN));
    }

    @Test
    void checkIgnoresAForeignPayload() {
        Fixture fixture = new Fixture();
        fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "Mallory", "not-the-token"));
        fixture.commands.add(new StartCommand(98L, "Mallory", null));

        TelegramLinkView view = fixture.service().check();

        assertThat(view.status()).isEqualTo(TelegramLinkStatus.PENDING);
        assertThat(view.chat()).isNull();
        assertThat(fixture.link.chat()).isNull();
        assertThat(fixture.link.pending()).isNotNull();
        assertThat(fixture.confirmations).isEmpty();
    }

    @Test
    void checkAfterExpiryDoesNotLinkAndAnswersUnlinked() {
        Fixture fixture = new Fixture();
        LinkInvitation invitation = fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "Alex", invitation.token()));
        fixture.now = NOW.plus(Duration.ofMinutes(10));

        TelegramLinkView view = fixture.service().check();

        assertThat(view.status()).isEqualTo(TelegramLinkStatus.UNLINKED);
        assertThat(fixture.link.chat()).isNull();
        assertThat(fixture.confirmations).isEmpty();
    }

    @Test
    void checkWithoutPendingTokenStillDrainsAndLinksNothing() {
        Fixture fixture = new Fixture();
        fixture.commands.add(new StartCommand(99L, "Alex", "anything"));

        TelegramLinkView view = fixture.service().check();

        assertThat(view.status()).isEqualTo(TelegramLinkStatus.UNLINKED);
        assertThat(fixture.drains).isEqualTo(1);
        assertThat(fixture.commands).isEmpty();
        assertThat(fixture.link).isEqualTo(TelegramLink.NONE);
        assertThat(fixture.confirmations).isEmpty();
    }

    @Test
    void relinkingReplacesThePreviousChat() {
        Fixture fixture = new Fixture();
        fixture.link = new TelegramLink(new LinkedChat(7L, "Old", NOW.minusSeconds(3600)), null);
        LinkInvitation invitation = fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "New", invitation.token()));

        TelegramLinkView view = fixture.service().check();

        assertThat(view.chat()).isEqualTo(new LinkedChat(99L, "New", NOW));
        assertThat(fixture.link.chat().chatId()).isEqualTo(99L);
        assertThat(fixture.link.pending()).isNull();
    }

    @Test
    void aLabelLongerThan100CharactersIsTruncated() {
        Fixture fixture = new Fixture();
        LinkInvitation invitation = fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "x".repeat(150), invitation.token()));

        TelegramLinkView view = fixture.service().check();

        assertThat(view.chat().label()).hasSize(100);
        assertThat(fixture.link.chat().label()).isEqualTo("x".repeat(100));
    }

    @Test
    void aLabelIsTruncatedWithoutSplittingAnEmoji() {
        Fixture fixture = new Fixture();
        LinkInvitation invitation = fixture.service().start();
        String emoji = "😀";
        fixture.commands.add(new StartCommand(99L, "x".repeat(99) + emoji + "y".repeat(10), invitation.token()));

        fixture.service().check();

        assertThat(fixture.link.chat().label()).isEqualTo("x".repeat(99) + emoji);
    }

    @Test
    void aFailedBotUsernameLookupSavesNoPendingToken() {
        Fixture fixture = new Fixture();
        LinkedChat chat = new LinkedChat(7L, "Alex", NOW.minusSeconds(60));
        fixture.link = new TelegramLink(chat, null);
        TelegramLink before = fixture.link;
        NotificationDeliveryException failure = new NotificationDeliveryException("getMe failed");
        fixture.botUsernameFailure = failure;

        assertThatThrownBy(() -> fixture.service().start()).isSameAs(failure);

        assertThat(fixture.link).isSameAs(before);
        assertThat(fixture.link.pending()).isNull();
    }

    @Test
    void aCommandIsMatchedAgainstTheLinkLoadedAfterDraining() {
        Fixture fixture = new Fixture();
        fixture.link = new TelegramLink(null, new PendingLink(sha256("old-token"), NOW.plusSeconds(60)));
        fixture.commands.add(new StartCommand(99L, "Alex", "new-token"));
        fixture.onDrain =
                () -> fixture.link = new TelegramLink(null, new PendingLink(sha256("new-token"), NOW.plusSeconds(60)));

        TelegramLinkView view = fixture.service().check();

        LinkedChat expected = new LinkedChat(99L, "Alex", NOW);
        assertThat(view).isEqualTo(new TelegramLinkView(TelegramLinkStatus.LINKED, expected));
        assertThat(fixture.confirmations).containsExactly(new Confirmation(99L, Language.FR));
    }

    @Test
    void aSecondCheckWithTheSameTokenAfterLinkingDoesNotLinkAgain() {
        Fixture fixture = new Fixture();
        LinkInvitation invitation = fixture.service().start();
        fixture.commands.add(new StartCommand(99L, "Alex", invitation.token()));
        fixture.service().check();
        fixture.commands.add(new StartCommand(55L, "Mallory", invitation.token()));

        TelegramLinkView view = fixture.service().check();

        LinkedChat expected = new LinkedChat(99L, "Alex", NOW);
        assertThat(view).isEqualTo(new TelegramLinkView(TelegramLinkStatus.LINKED, expected));
        assertThat(fixture.link).isEqualTo(new TelegramLink(expected, null));
        assertThat(fixture.confirmations).containsExactly(new Confirmation(99L, Language.FR));
    }

    @Test
    void unlinkClearsChatAndPendingToken() {
        Fixture fixture = new Fixture();
        fixture.link = new TelegramLink(
                new LinkedChat(7L, "Alex", NOW.minusSeconds(60)), new PendingLink("hash", NOW.plusSeconds(60)));

        fixture.service().unlink();

        assertThat(fixture.link).isEqualTo(TelegramLink.NONE);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Confirmation(long chatId, Language language) {}

    private static final class Fixture {

        TelegramLink link = TelegramLink.NONE;
        boolean configured = true;
        Language language = Language.FR;
        Instant now = NOW;
        int drains;
        RuntimeException botUsernameFailure;
        Runnable onDrain = () -> {};
        final List<StartCommand> commands = new ArrayList<>();
        final List<Confirmation> confirmations = new ArrayList<>();

        TelegramLinkService service() {
            TelegramLinkPort links = new TelegramLinkPort() {
                @Override
                public TelegramLink load() {
                    return link;
                }

                @Override
                public TelegramLink save(TelegramLink saved) {
                    link = saved;
                    return saved;
                }
            };
            TelegramBotPort bot = new TelegramBotPort() {
                @Override
                public boolean configured() {
                    return configured;
                }

                @Override
                public String botUsername() {
                    if (botUsernameFailure != null) {
                        throw botUsernameFailure;
                    }
                    return "cairn_bot";
                }

                @Override
                public List<StartCommand> drainStartCommands() {
                    drains++;
                    onDrain.run();
                    List<StartCommand> drained = new ArrayList<>(commands);
                    commands.clear();
                    return drained;
                }

                @Override
                public void confirmLinked(long chatId, Language confirmedLanguage) {
                    confirmations.add(new Confirmation(chatId, confirmedLanguage));
                }
            };
            Clock clock = new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now;
                }
            };
            return new TelegramLinkService(links, bot, () -> new Preferences(language, Theme.SYSTEM), clock);
        }
    }
}
