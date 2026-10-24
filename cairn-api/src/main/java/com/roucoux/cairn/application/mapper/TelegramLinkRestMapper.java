package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.LinkInvitation;
import com.roucoux.cairn.domain.model.TelegramLinkView;
import com.roucoux.cairn.generated.model.TelegramInvitationResponse;
import com.roucoux.cairn.generated.model.TelegramLinkResponse;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TelegramLinkRestMapper {

    public TelegramLinkResponse toResponse(TelegramLinkView view) {
        TelegramLinkResponse response = new TelegramLinkResponse(
                TelegramLinkResponse.StatusEnum.fromValue(view.status().name().toLowerCase(Locale.ROOT)));
        if (view.chat() != null) {
            response.setChatLabel(view.chat().label());
            response.setLinkedAt(view.chat().linkedAt().atOffset(ZoneOffset.UTC));
        }
        return response;
    }

    public TelegramInvitationResponse toResponse(LinkInvitation invitation) {
        return new TelegramInvitationResponse(
                "https://t.me/" + invitation.botUsername() + "?start=" + invitation.token(),
                invitation.expiresAt().atOffset(ZoneOffset.UTC));
    }
}
