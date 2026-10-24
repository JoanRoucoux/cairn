package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.LinkInvitation;
import com.roucoux.cairn.domain.model.TelegramLinkView;

public interface LinkTelegramUseCase {

    TelegramLinkView status();

    LinkInvitation start();

    TelegramLinkView check();

    void unlink();
}
