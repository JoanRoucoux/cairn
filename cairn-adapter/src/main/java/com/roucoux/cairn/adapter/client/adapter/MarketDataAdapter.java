package com.roucoux.cairn.adapter.client.adapter;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Outbound adapter: implements the domain's market data port against an external quote API. */
@Component
class MarketDataAdapter implements MarketDataPort {

    private final RestClient restClient;

    MarketDataAdapter(RestClient marketDataRestClient) {
        this.restClient = marketDataRestClient;
    }

    @Override
    public Optional<BigDecimal> currentPrice(String isin) {
        try {
            MarketDataQuote quote =
                    restClient.get().uri("/quotes/{isin}", isin).retrieve().body(MarketDataQuote.class);
            return Optional.ofNullable(quote).map(MarketDataQuote::price);
        } catch (HttpClientErrorException.NotFound notFound) {
            // No quote for this instrument: a business outcome, surfaced as an empty result.
            return Optional.empty();
        } catch (RestClientException failure) {
            // Provider unreachable, timed out or answered with an error: a technical failure.
            throw new MarketDataUnavailableException(isin, failure);
        }
    }

    record MarketDataQuote(String isin, BigDecimal price) {}
}
