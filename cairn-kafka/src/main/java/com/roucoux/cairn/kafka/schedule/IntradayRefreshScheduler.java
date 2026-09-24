package com.roucoux.cairn.kafka.schedule;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes quotes every 15 minutes: equities and ETFs during Euronext hours, cryptos around the
 * clock. Each run asks the {@link ObjectProvider} for a fresh {@link RefreshQuotesUseCase}, whose
 * prototype-scoped chain reaches down to a fresh {@code CoinGeckoQuoteAdapter} instance
 * (see {@code WorkerDomainConfig}) — a singleton would keep serving the first run's crypto prices
 * forever.
 */
@Component
class IntradayRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntradayRefreshScheduler.class);

    private final ObjectProvider<RefreshQuotesUseCase> refreshQuotesProvider;
    private final Heartbeat heartbeat;

    IntradayRefreshScheduler(ObjectProvider<RefreshQuotesUseCase> refreshQuotesProvider, Heartbeat heartbeat) {
        this.refreshQuotesProvider = refreshQuotesProvider;
        this.heartbeat = heartbeat;
    }

    @Scheduled(cron = "0 0/15 9-17 * * MON-FRI", zone = "Europe/Paris")
    void refreshEquitiesAndEtfs() {
        refresh(Set.of(AssetClass.EQUITY, AssetClass.ETF));
    }

    @Scheduled(cron = "0 0/15 * * * *", zone = "Europe/Paris")
    void refreshCryptos() {
        refresh(Set.of(AssetClass.CRYPTO));
    }

    private void refresh(Set<AssetClass> assetClasses) {
        try {
            refreshQuotesProvider.getObject().refreshAll(assetClasses, RefreshTrigger.SCHEDULER);
            heartbeat.ping();
        } catch (RuntimeException e) {
            log.error("Intraday refresh failed for {}", assetClasses, e);
        }
    }
}
