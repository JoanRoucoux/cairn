package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.ManagePreferencesUseCase;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.SavePreferencesPort;
import com.roucoux.cairn.domain.service.PreferencesService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PreferencesDomainConfig {

    @Bean
    ManagePreferencesUseCase managePreferencesUseCase(LoadPreferencesPort load, SavePreferencesPort save) {
        return new PreferencesService(load, save);
    }
}
