package com.roucoux.cairn.domain.exception.technical;

/**
 * Thrown when {@code SendNotificationPort} could not deliver a notification. Its failure contract
 * lives in the domain alongside the outbound port; the outbound adapter raises it. The message
 * never carries the request URI or any credential: a Telegram call embeds the bot token in its URL.
 */
public class NotificationDeliveryException extends TechnicalException {

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
