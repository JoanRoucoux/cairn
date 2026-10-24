package com.roucoux.cairn.domain.exception.business;

public class TelegramNotConfiguredException extends BusinessException {

    public TelegramNotConfiguredException() {
        super("Telegram bot is not configured");
    }
}
