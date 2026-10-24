package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Summary;

public interface SendNotificationPort {

    void send(Language language, Summary summary);
}
