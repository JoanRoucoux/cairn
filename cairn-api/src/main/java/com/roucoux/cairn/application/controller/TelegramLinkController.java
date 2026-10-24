package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.TelegramLinkRestMapper;
import com.roucoux.cairn.domain.port.in.LinkTelegramUseCase;
import com.roucoux.cairn.generated.api.TelegramApi;
import com.roucoux.cairn.generated.model.TelegramInvitationResponse;
import com.roucoux.cairn.generated.model.TelegramLinkResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TelegramLinkController implements TelegramApi {

    private final LinkTelegramUseCase telegram;
    private final TelegramLinkRestMapper mapper;

    TelegramLinkController(LinkTelegramUseCase telegram, TelegramLinkRestMapper mapper) {
        this.telegram = telegram;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TelegramLinkResponse> getTelegramLink() {
        return ResponseEntity.ok(mapper.toResponse(telegram.status()));
    }

    @Override
    public ResponseEntity<TelegramInvitationResponse> startTelegramLink() {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(telegram.start()));
    }

    @Override
    public ResponseEntity<TelegramLinkResponse> checkTelegramLink() {
        return ResponseEntity.ok(mapper.toResponse(telegram.check()));
    }

    @Override
    public ResponseEntity<Void> unlinkTelegram() {
        telegram.unlink();
        return ResponseEntity.noContent().build();
    }
}
