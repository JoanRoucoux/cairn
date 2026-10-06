package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.Isin;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(1)
public class YahooResolutionAdapter implements ResolveInstrumentPort {

    private final RestClient client;

    public YahooResolutionAdapter(@Qualifier("yahooRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.YAHOO;
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        try {
            SearchResponse response = client.get()
                    .uri("/v1/finance/search?q={query}&quotesCount=5&newsCount=0", query)
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.quotes() == null) {
                return List.of();
            }
            String isin = isinOf(query);
            return response.quotes().stream()
                    .filter(quote -> assetClassOf(quote.quoteType()).isPresent())
                    .map(quote -> toCandidate(quote, isin))
                    .toList();
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("Yahoo search failed: " + failure.getMessage());
        }
    }

    private static String isinOf(String query) {
        String normalised = query.strip().toUpperCase(Locale.ROOT);
        return Isin.isValid(normalised) ? normalised : null;
    }

    private static InstrumentCandidate toCandidate(SearchQuote quote, String isin) {
        return new InstrumentCandidate(
                nameOf(quote),
                PriceSource.YAHOO,
                quote.symbol(),
                assetClassOf(quote.quoteType()).orElseThrow(),
                quote.exchDisp(),
                isin,
                quote.symbol(),
                null,
                null);
    }

    private static String nameOf(SearchQuote quote) {
        if (quote.longname() != null && !quote.longname().isBlank()) {
            return quote.longname();
        }
        return quote.shortname() != null && !quote.shortname().isBlank() ? quote.shortname() : quote.symbol();
    }

    static Optional<AssetClass> assetClassOf(String quoteType) {
        if (quoteType == null) {
            return Optional.empty();
        }
        return switch (quoteType) {
            case "EQUITY" -> Optional.of(AssetClass.EQUITY);
            case "ETF" -> Optional.of(AssetClass.ETF);
            case "MUTUALFUND" -> Optional.of(AssetClass.FUND);
            case "CRYPTOCURRENCY" -> Optional.of(AssetClass.CRYPTO);
            default -> Optional.empty();
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<SearchQuote> quotes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchQuote(String symbol, String longname, String shortname, String quoteType, String exchDisp) {}
}
