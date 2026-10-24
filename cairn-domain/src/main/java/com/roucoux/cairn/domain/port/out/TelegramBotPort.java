package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.StartCommand;
import java.util.List;

public interface TelegramBotPort {

    boolean configured();

    String botUsername();

    List<StartCommand> drainStartCommands();

    void confirmLinked(long chatId, Language language);
}
