package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.StartCommand;
import com.roucoux.cairn.domain.model.TelegramLink;
import com.roucoux.cairn.domain.model.TelegramLinkStatus;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.domain.port.in.LinkTelegramUseCase;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.TelegramBotPort;
import com.roucoux.cairn.domain.port.out.TelegramLinkPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TelegramLinkDomainConfigTest {

    @Test
    void wiresTheLinkSliceSoAMatchingStartLinksTheChat() {
        AtomicReference<TelegramLink> stored = new AtomicReference<>(new TelegramLink(null, null));
        TelegramLinkPort links = new TelegramLinkPort() {
            @Override
            public TelegramLink load() {
                return stored.get();
            }

            @Override
            public TelegramLink save(TelegramLink link) {
                stored.set(link);
                return link;
            }
        };
        AtomicReference<String> token = new AtomicReference<>();
        TelegramBotPort bot = new TelegramBotPort() {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public String botUsername() {
                return "cairn_test_bot";
            }

            @Override
            public List<StartCommand> drainStartCommands() {
                return List.of(new StartCommand(42L, "Alex", token.get()));
            }

            @Override
            public void confirmLinked(long chatId, Language language) {}
        };
        LoadPreferencesPort preferences = () -> new Preferences(Language.FR, Theme.SYSTEM);
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T08:00:00Z"), ZoneOffset.UTC);

        LinkTelegramUseCase useCase =
                new TelegramLinkDomainConfig().linkTelegramUseCase(links, bot, preferences, clock);
        token.set(useCase.start().token());

        assertThat(useCase.check().status()).isEqualTo(TelegramLinkStatus.LINKED);
        assertThat(stored.get().chat().chatId()).isEqualTo(42L);
    }
}
