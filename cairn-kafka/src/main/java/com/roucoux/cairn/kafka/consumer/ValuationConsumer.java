package com.roucoux.cairn.kafka.consumer;

import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import com.roucoux.cairn.events.Topics;
import java.time.Clock;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(id = "cairn-valuation", topics = Topics.PORTFOLIO, groupId = "cairn-valuation")
public class ValuationConsumer {

    private final RecordValuationUseCase recordValuation;
    private final Clock clock;

    public ValuationConsumer(RecordValuationUseCase recordValuation, Clock clock) {
        this.recordValuation = recordValuation;
        this.clock = clock;
    }

    @KafkaHandler
    void onRefreshCompleted(RefreshCompletedEvent event) {
        recordValuation.record(clock.instant());
    }

    @KafkaHandler(isDefault = true)
    void ignore(Object other) {}
}
