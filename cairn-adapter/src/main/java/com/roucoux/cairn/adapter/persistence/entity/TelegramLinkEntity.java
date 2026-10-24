package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.LinkedChat;
import com.roucoux.cairn.domain.model.PendingLink;
import com.roucoux.cairn.domain.model.TelegramLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "telegram_link")
public class TelegramLinkEntity {

    public static final short SINGLE_ROW_ID = 1;

    @Id
    private short id;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "chat_label", length = 100)
    private String chatLabel;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pending_token_hash", length = 64)
    private String pendingTokenHash;

    @Column(name = "pending_expires_at")
    private Instant pendingExpiresAt;

    protected TelegramLinkEntity() {}

    public static TelegramLinkEntity fromDomain(TelegramLink link) {
        TelegramLinkEntity entity = new TelegramLinkEntity();
        entity.id = SINGLE_ROW_ID;
        if (link.chat() != null) {
            entity.chatId = link.chat().chatId();
            entity.chatLabel = link.chat().label();
            entity.linkedAt = link.chat().linkedAt();
        }
        if (link.pending() != null) {
            entity.pendingTokenHash = link.pending().tokenHash();
            entity.pendingExpiresAt = link.pending().expiresAt();
        }
        return entity;
    }

    public TelegramLink toDomain() {
        LinkedChat chat = chatId == null ? null : new LinkedChat(chatId, chatLabel, linkedAt);
        PendingLink pending = pendingTokenHash == null ? null : new PendingLink(pendingTokenHash, pendingExpiresAt);
        return new TelegramLink(chat, pending);
    }
}
