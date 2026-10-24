package com.roucoux.cairn.adapter.client.adapter;

import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.StartCommand;
import com.roucoux.cairn.domain.port.out.TelegramBotPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TelegramBotAdapter implements TelegramBotPort {

    private static final int BATCH_SIZE = 100;
    private static final String START_PREFIX = "/start ";

    private final TelegramBotApi api;
    private final MessageSource messages;
    private volatile String botUsername;

    @Autowired
    TelegramBotAdapter(TelegramBotApi api, @Qualifier("telegramMessages") MessageSource messages) {
        this.api = api;
        this.messages = messages;
    }

    @Override
    public boolean configured() {
        return api.configured();
    }

    @Override
    public String botUsername() {
        String known = botUsername;
        if (known == null) {
            known = api.getMe().path("username").asString("");
            if (known.isBlank()) {
                throw new NotificationDeliveryException("Telegram answered without a bot username");
            }
            botUsername = known;
        }
        return known;
    }

    @Override
    public List<StartCommand> drainStartCommands() {
        JsonNode updates = api.getUpdates(null, BATCH_SIZE);
        List<StartCommand> commands = new ArrayList<>();
        long maxUpdateId = -1;
        boolean any = false;
        for (JsonNode update : updates) {
            any = true;
            maxUpdateId = Math.max(maxUpdateId, update.path("update_id").asLong());
            JsonNode message = update.path("message");
            JsonNode chat = message.path("chat");
            String text = message.path("text").asString("");
            if ("private".equals(chat.path("type").asString()) && text.startsWith(START_PREFIX)) {
                commands.add(
                        new StartCommand(chat.path("id").asLong(), label(chat), text.substring(START_PREFIX.length())));
            }
        }
        if (any) {
            api.getUpdates(maxUpdateId + 1, 1);
        }
        return commands;
    }

    @Override
    public void confirmLinked(long chatId, Language language) {
        api.sendMessage(chatId, messages.getMessage("linked", null, TelegramNumbers.locale(language)));
    }

    private static String label(JsonNode chat) {
        String username = chat.path("username").asString("");
        return username.isBlank() ? chat.path("first_name").asString("") : "@" + username;
    }
}
