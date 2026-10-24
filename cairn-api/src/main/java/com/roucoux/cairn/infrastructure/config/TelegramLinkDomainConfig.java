package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.LinkTelegramUseCase;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.TelegramBotPort;
import com.roucoux.cairn.domain.port.out.TelegramLinkPort;
import com.roucoux.cairn.domain.service.TelegramLinkService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TelegramLinkDomainConfig {

    @Bean
    LinkTelegramUseCase linkTelegramUseCase(
            TelegramLinkPort links, TelegramBotPort bot, LoadPreferencesPort preferences, Clock clock) {
        return new TelegramLinkService(links, bot, preferences, clock);
    }
}
