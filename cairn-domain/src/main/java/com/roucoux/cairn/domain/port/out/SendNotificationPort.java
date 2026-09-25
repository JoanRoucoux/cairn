package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.DailySummary;

public interface SendNotificationPort {

    void send(DailySummary summary);
}
