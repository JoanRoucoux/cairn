package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
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
            return response.quotes().stream()
                    .map(YahooResolutionAdapter::toCandidate)
                    .toList();
        } catch (RestClientException failure) {
            return List.of();
        }
    }

    private static InstrumentCandidate toCandidate(SearchQuote quote) {
        return new InstrumentCandidate(
                quote.longname(), PriceSource.YAHOO, quote.symbol(), assetClassOf(quote.quoteType()), null);
    }

    static AssetClass assetClassOf(String quoteType) {
        return switch (quoteType) {
            case "ETF" -> AssetClass.ETF;
            case "MUTUALFUND" -> AssetClass.FUND;
            default -> AssetClass.EQUITY;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<SearchQuote> quotes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchQuote(String symbol, String longname, String quoteType) {}
}
