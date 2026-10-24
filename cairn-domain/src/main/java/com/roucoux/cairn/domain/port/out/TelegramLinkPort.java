package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.TelegramLink;

public interface TelegramLinkPort {

    TelegramLink load();

    TelegramLink save(TelegramLink link);
}
