package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.TelegramNotConfiguredException;
import com.roucoux.cairn.domain.model.LinkInvitation;
import com.roucoux.cairn.domain.model.LinkedChat;
import com.roucoux.cairn.domain.model.PendingLink;
import com.roucoux.cairn.domain.model.StartCommand;
import com.roucoux.cairn.domain.model.TelegramLink;
import com.roucoux.cairn.domain.model.TelegramLinkStatus;
import com.roucoux.cairn.domain.model.TelegramLinkView;
import com.roucoux.cairn.domain.port.in.LinkTelegramUseCase;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.TelegramBotPort;
import com.roucoux.cairn.domain.port.out.TelegramLinkPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

public class TelegramLinkService implements LinkTelegramUseCase {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_LABEL_LENGTH = 100;

    private final SecureRandom random = new SecureRandom();
    private final TelegramLinkPort links;
    private final TelegramBotPort bot;
    private final LoadPreferencesPort preferences;
    private final Clock clock;

    public TelegramLinkService(
            TelegramLinkPort links, TelegramBotPort bot, LoadPreferencesPort preferences, Clock clock) {
        this.links = links;
        this.bot = bot;
        this.preferences = preferences;
        this.clock = clock;
    }

    @Override
    public TelegramLinkView status() {
        if (!bot.configured()) {
            return new TelegramLinkView(TelegramLinkStatus.UNAVAILABLE, null);
        }
        return view(links.load());
    }

    @Override
    public LinkInvitation start() {
        requireConfigured();
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        TelegramLink current = links.load();
        links.save(new TelegramLink(current.chat(), new PendingLink(hash(token), expiresAt)));
        return new LinkInvitation(bot.botUsername(), token, expiresAt);
    }

    @Override
    public TelegramLinkView check() {
        requireConfigured();
        Instant now = clock.instant();
        TelegramLink current = links.load();
        PendingLink pending = current.pending();
        boolean open = pending != null && now.isBefore(pending.expiresAt());
        for (StartCommand command : bot.drainStartCommands()) {
            if (open && matches(pending, command)) {
                LinkedChat chat = new LinkedChat(command.chatId(), truncate(command.label()), now);
                TelegramLink linked = links.save(new TelegramLink(chat, null));
                bot.confirmLinked(command.chatId(), preferences.load().language());
                return view(linked);
            }
        }
        return view(current);
    }

    @Override
    public void unlink() {
        links.save(TelegramLink.NONE);
    }

    private void requireConfigured() {
        if (!bot.configured()) {
            throw new TelegramNotConfiguredException();
        }
    }

    private TelegramLinkView view(TelegramLink link) {
        return new TelegramLinkView(link.status(clock.instant()), link.chat());
    }

    private static boolean matches(PendingLink pending, StartCommand command) {
        if (command.payload() == null) {
            return false;
        }
        return MessageDigest.isEqual(
                pending.tokenHash().getBytes(StandardCharsets.UTF_8),
                hash(command.payload()).getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String label) {
        if (label == null || label.length() <= MAX_LABEL_LENGTH) {
            return label;
        }
        return label.substring(0, MAX_LABEL_LENGTH);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
